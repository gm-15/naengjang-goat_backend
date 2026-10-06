#!/usr/bin/env python3
"""Exercise the demo HTTP flow without printing passwords or bearer tokens.
Use a new dedicated demo database for each run. Requires only Python 3 stdlib.
"""
import argparse
import datetime
import json
import os
import pathlib
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--output-dir", default="/tmp/goat-demo")
    args = parser.parse_args()
    token = None

    def request(method, path, payload=None, data=None, content_type=None):
        headers = {}
        if token:
            headers["Authorization"] = "Bearer " + token
        if payload is not None:
            data = json.dumps(payload).encode()
            content_type = "application/json"
        if content_type:
            headers["Content-Type"] = content_type
        req = urllib.request.Request(args.base_url.rstrip("/") + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                raw = response.read()
                return json.loads(raw) if "json" in response.headers.get("Content-Type", "") else raw
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"{method} {path}: HTTP {error.code}: {error.read().decode()}") from None

    auth = request("POST", "/api/users/login", {"username": "demo", "password": os.getenv("DEMO_PASSWORD", "demo1234")})
    token = auth["accessToken"]
    setup = request("POST", "/demo/setup", {})
    if setup["alreadyInitialized"]:
        raise RuntimeError("Demo was already initialized. Use a new dedicated demo database; no data is reset automatically.")
    day = setup["businessDate"]
    ingredient_id = setup["ingredientId"]
    sample = request("GET", "/demo/pos-sample")
    output = pathlib.Path(args.output_dir)
    output.mkdir(parents=True, exist_ok=True)
    (output / "POS_시연.xlsx").write_bytes(sample)
    boundary = "Goat" + uuid.uuid4().hex
    multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="pos.xlsx"\r\n'
                 'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode() + sample + f"\r\n--{boundary}--\r\n".encode()
    uploaded = request("POST", "/pos/uploads", data=multipart, content_type="multipart/form-data; boundary=" + boundary)
    assert uploaded["salesQuantity"] == 8 and uploaded["salesAmount"] == 64000
    assert uploaded["ingredientConsumption"][0]["quantity"] == 4000
    duplicate = request("POST", "/pos/uploads", data=multipart, content_type="multipart/form-data; boundary=" + boundary)
    assert duplicate["duplicate"] and duplicate["uploadId"] == uploaded["uploadId"]

    def current_stock():
        report = request("GET", "/reports/daily?businessDate=" + day)
        stock = next(i["quantity"] for i in report["currentInventory"] if i["ingredientId"] == ingredient_id)
        assert report["salesAmount"] == 64000 and report["ingredientConsumption"][0]["quantity"] == 4000
        return stock

    assert current_stock() == 2000
    recommendation = request("GET", "/closing/recommendations?businessDate=" + day)
    item = next(i for i in recommendation["items"] if i["ingredientId"] == ingredient_id)
    assert item["dailyAvgSales"] == 1100 and item["recommendedQuantity"] == 1300 and item["buySignal"]
    order = request("POST", "/purchase-orders", {
        "ingredientId": ingredient_id, "quantity": 2, "baseUnit": "kg", "unitPrice": 5000,
        "supplier": "시연용 구매기록", "productName": "배추 2kg", "sourceUrl": "https://example.com/demo"
    })
    assert order["deliveryStatus"] == "WAITING" and order["expectedQuantityBase"] == 2000
    assert current_stock() == 2000
    tomorrow = (datetime.date.fromisoformat(day) + datetime.timedelta(days=1)).isoformat()
    expiry = (datetime.date.fromisoformat(day) + datetime.timedelta(days=10)).isoformat()
    received = request("POST", f'/purchase-orders/{order["id"]}/receive', {"receivedQuantity": 2000, "expirationDate": expiry})
    assert received["deliveryStatus"] == "RECEIVED" and current_stock() == 4000
    alert = request("POST", "/demo/opening-trigger?businessDate=" + tomorrow, {})
    again = request("POST", "/demo/opening-trigger?businessDate=" + tomorrow, {})
    assert alert["notificationId"] == again["notificationId"]
    detail = request("GET", f'/closing/notifications/{alert["notificationId"]}')
    (output / "results.json").write_text(json.dumps({"setup": setup, "upload": uploaded, "recommendations": recommendation,
        "purchase": order, "receipt": received, "openingNotification": detail}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("PASS: POS 8 / 매출 64,000 / 소비 4,000g / 재고 6,000→2,000→(발주)2,000→(입고)4,000g")
    print("PASS: 30일 평균 1,100g / 발주 제안 1,300g / 시연 가격 신호 / 중복 업로드·영업 전 트리거")
    print("알림 상태:", detail["deliveryStatus"], "(SENT는 FCM 요청 성공이며 실기기 표시 검증은 별도)")
    print("샘플과 결과:", output)


if __name__ == "__main__":
    main()
