#!/usr/bin/env node
/**
 * Owner workflow against a REAL isolated demo backend. No Firebase sends or purchases.
 * Install Playwright outside the repository and set QA_TOOLS_DIR to that directory.
 * Requires a fresh demo database; /demo/setup never overwrites existing records.
 * WEB_BASE_URL=http://127.0.0.1:5174 API_BASE_URL=http://127.0.0.1:8082 \
 * QA_TOOLS_DIR=/tmp/goat-qa node scripts/owner-flow-e2e.mjs
 */
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

const web = (process.env.WEB_BASE_URL || 'http://127.0.0.1:5174').replace(/\/$/, '');
const api = (process.env.API_BASE_URL || 'http://127.0.0.1:8082').replace(/\/$/, '');
const output = path.resolve(process.env.QA_OUTPUT_DIR || '/workspace/scratch/integration-qa');
const require = createRequire(path.resolve(process.env.QA_TOOLS_DIR || '/workspace/scratch/qa-tools', 'package.json'));
const { chromium } = require('playwright');
const evidence = { web, api, startedAt: new Date().toISOString(), checks: [], screenshots: [] };
const browserErrors = [];
const secrets = [];
let bearer;
let browser;
let page;

async function request(method, endpoint, payload, options = {}) {
  const headers = {};
  if (options.token || bearer) headers.Authorization = `Bearer ${options.token || bearer}`;
  if (payload !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${api}${endpoint}`, {
    method, headers, body: payload !== undefined ? JSON.stringify(payload) : undefined,
  });
  if (!response.ok) throw new Error(`${method} ${endpoint}: HTTP ${response.status}`);
  if (options.binary) return Buffer.from(await response.arrayBuffer());
  const text = await response.text();
  try { return JSON.parse(text); } catch { return text; }
}

function pass(name, values = {}) {
  evidence.checks.push({ name, ...values });
  console.log(`PASS: ${name}`);
}

async function screenshot(name) {
  const file = `${name}.png`;
  await page.screenshot({ path: path.join(output, file), fullPage: true });
  evidence.screenshots.push(file);
}

async function uiMutation(method, endpoint, action, expectedStatus = 200) {
  const [response] = await Promise.all([
    page.waitForResponse(r => new URL(r.url()).pathname === endpoint && r.request().method() === method),
    action(),
  ]);
  assert.equal(response.status(), expectedStatus, `Unexpected HTTP status for ${endpoint}`);
  return response.json();
}

async function uiPost(endpoint, action, expectedStatus = 200) {
  return uiMutation('POST', endpoint, action, expectedStatus);
}

function sanitized(value) {
  let text = String(value);
  for (const secret of secrets) if (secret) text = text.replaceAll(secret, '[redacted]');
  return text;
}

async function login(username, password) {
  await page.goto(`${web}/`);
  await page.getByPlaceholder('아이디를 입력하세요').fill(username);
  await page.locator('input[type="password"]').fill(password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await page.waitForURL('**/main');
}

async function upload(file, status = 200) {
  await page.getByTestId('pos-file').setInputFiles(file);
  return uiPost('/pos/uploads', () => page.getByTestId('pos-upload').click(), status);
}

function future(day, amount) {
  const date = new Date(`${day}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + amount);
  return date.toISOString().slice(0, 10);
}

try {
  await mkdir(output, { recursive: true });
  const credentials = { username: 'demo', password: process.env.DEMO_PASSWORD || 'demo1234' };
  secrets.push(credentials.password);
  const auth = await request('POST', '/api/users/login', credentials);
  bearer = auth.accessToken;
  secrets.push(bearer, auth.refreshToken);
  const setup = await request('POST', '/demo/setup', {});
  assert.ok(!setup.alreadyInitialized || process.env.QA_ALLOW_EXISTING === '1', 'Use a fresh isolated demo database for a complete run');
  const openingReport = await request('GET', `/reports/daily?businessDate=${setup.businessDate}`);
  const openingStock = Number(openingReport.currentInventory.find(item => item.ingredientId === setup.ingredientId)?.quantity || 0);
  evidence.freshFixture = !openingReport.uploaded && openingStock === Number(setup.initialStock);
  assert.ok(evidence.freshFixture || process.env.QA_ALLOW_EXISTING === '1', 'Fresh fixture must start before POS upload with the expected initial stock');
  evidence.businessDate = setup.businessDate;
  evidence.ingredientId = setup.ingredientId;

  async function report() {
    return request('GET', `/reports/daily?businessDate=${setup.businessDate}`);
  }
  async function stock() {
    const data = await report();
    return Number(data.currentInventory.find(item => item.ingredientId === setup.ingredientId)?.quantity || 0);
  }

  const appliedSamplePath = path.join(output, `POS_applied_${setup.businessDate}.xlsx`);
  let sample;
  if (openingReport.uploaded) {
    sample = await readFile(appliedSamplePath).catch(() => {
      throw new Error('The applied POS sample is not cached. Use a fresh isolated demo database and output directory.');
    });
  } else {
    const sampleResponse = await fetch(`${api}/demo/pos-sample`, { headers: { Authorization: `Bearer ${bearer}` } });
    assert.equal(sampleResponse.status, 200);
    sample = Buffer.from(await sampleResponse.arrayBuffer());
  }
  const samplePath = path.join(output, 'POS_sample.xlsx');
  const invalidPath = path.join(output, 'invalid.xlsx');
  const conflictPath = path.join(output, 'same_day_other_file.xlsx');
  await writeFile(samplePath, sample);
  await writeFile(invalidPath, 'invalid workbook');
  execFileSync('python3', ['-c',
    'import sys,zipfile\nwith zipfile.ZipFile(sys.argv[1]) as src, zipfile.ZipFile(sys.argv[2],"w") as dst:\n for item in src.infolist(): dst.writestr(item,src.read(item.filename))\n dst.comment=b"QA same business day, different file hash"', samplePath, conflictPath]);

  browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ko-KR', timezoneId: 'Asia/Seoul' });
  page = await context.newPage();
  page.setDefaultTimeout(15000);
  page.on('pageerror', error => browserErrors.push(error.message));
  const mutationPaths = [];
  page.on('request', request => {
    if (request.method() === 'POST') mutationPaths.push(new URL(request.url()).pathname);
  });
  await login(credentials.username, credentials.password);
  pass('브라우저 실제 로그인');

  await page.goto(`${web}/operations`);
  await page.getByTestId('pos-file').waitFor();
  const beforeInvalid = await stock();
  const invalid = await upload(invalidPath, 400);
  assert.ok(invalid.message || invalid.detail, 'Invalid upload must supply a usable error');
  await page.getByRole('alert').filter({ hasText: invalid.message || invalid.detail }).first().waitFor();
  assert.equal(await stock(), beforeInvalid);
  await screenshot('01-invalid-pos');
  pass('잘못된 엑셀 거절 및 재고 보존', { status: 400 });

  const uploaded = await upload(samplePath);
  await writeFile(appliedSamplePath, sample);
  assert.equal(uploaded.businessDate, setup.businessDate);
  assert.equal(uploaded.salesQuantity, 8);
  assert.equal(Number(uploaded.salesAmount), 64000);
  assert.equal(Number(uploaded.ingredientConsumption[0].quantity), 4000);
  if (evidence.freshFixture) {
    assert.equal(uploaded.duplicate, false);
    assert.equal(await stock(), 2000);
  }
  await page.getByText(/64,000/).first().waitFor();
  await screenshot('02-pos-reflected');
  pass('POS 업로드 → 판매 8·매출 64,000·소비 4,000g', {
    freshFixture: evidence.freshFixture, stockBefore: openingStock, stockAfter: await stock(),
    salesQuantity: uploaded.salesQuantity, salesAmount: Number(uploaded.salesAmount), consumption: Number(uploaded.ingredientConsumption[0].quantity),
  });

  const afterUpload = await stock();
  const duplicate = await upload(samplePath);
  assert.equal(duplicate.duplicate, true);
  assert.equal(duplicate.uploadId, uploaded.uploadId);
  assert.equal(await stock(), afterUpload);
  pass('동일 파일 재업로드 시 재고 중복 차감 방지');

  const conflict = await upload(conflictPath, 409);
  assert.ok(conflict.message || conflict.detail);
  await page.getByRole('alert').filter({ hasText: conflict.message || conflict.detail }).first().waitFor();
  assert.equal(await stock(), afterUpload);
  await screenshot('03-same-day-conflict');
  pass('같은 영업일 다른 파일 충돌 안내 및 재고 보존', { status: 409 });

  const daily = await report();
  assert.equal(Number(daily.salesAmount), 64000);
  assert.equal(daily.salesQuantity, 8);
  assert.equal(Number(daily.ingredientConsumption[0].quantity), 4000);
  await page.getByTestId('report-date').fill(setup.businessDate);
  await page.getByRole('cell', { name: '배추국', exact: true }).waitFor();
  await screenshot('04-daily-report');
  pass('하루 운영 리포트의 판매·매출·소비·현재재고 실제 API 연결');

  await page.goto(`${web}/order`);
  const recommendations = await request('GET', `/closing/recommendations?businessDate=${setup.businessDate}`);
  const item = recommendations.items.find(item => item.ingredientId === setup.ingredientId);
  assert.equal(Number(item.dailyAvgSales), 1100);
  assert.equal(item.buySignal, true);
  if (afterUpload === 2000) assert.equal(Number(item.recommendedQuantity), 1300);
  await page.getByText('시연용 배추', { exact: true }).first().waitFor();
  await screenshot('05-closing-recommendation');
  pass('소비 이력과 시세를 반영한 최신 발주 판단');

  // Only the catalog product and external page are fixtures; every business API is real.
  const catalogPattern = `**/prices/${setup.ingredientId}`;
  await context.route(catalogPattern, async route => {
    const response = await route.fetch();
    const data = await response.json();
    data.onlinePrices = [{ source: 'QA', sourceLabel: '시연 쇼핑몰', productName: '외부 상품 배추 2kg',
      productUrl: 'https://example.com/qa-product', imageUrl: null, price: 12000, currency: 'KRW',
      isDiscount: false, weightGrams: 2000, unitPricePerKg: 6000, isLowest: true, fetchedAt: new Date().toISOString() }];
    await route.fulfill({ response, json: data });
  });
  await context.route('https://example.com/qa-product', route => route.fulfill({ contentType: 'text/html', body: '<p>QA external page: no checkout</p>' }));
  await page.goto(`${web}/lowest-price/${setup.ingredientId}`);
  await page.getByTestId('online-purchase-0').click();
  await page.bringToFront();
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  if (!await page.getByTestId('product-name').isVisible()) await page.getByTestId('purchase-open').click();
  assert.equal(await page.getByTestId('product-name').inputValue(), '외부 상품 배추 2kg');
  assert.equal(await page.getByTestId('source-url').inputValue(), 'https://example.com/qa-product');
  assert.equal(await page.getByTestId('purchase-unit').inputValue(), '포장');
  assert.equal(await page.getByTestId('package-size').inputValue(), '2000');
  assert.equal(await page.getByTestId('package-unit').inputValue(), 'g');
  assert.equal(await page.getByTestId('purchase-price').inputValue(), '12000');
  await screenshot('06-external-product-prefill');
  await page.getByRole('button', { name: '구매하지 않았어요', exact: true }).click();
  for (const extra of context.pages()) if (extra !== page) await extra.close();
  await context.unroute(catalogPattern);
  pass('외부 상품에서 돌아오면 상품명·링크·포장 내용량·가격 기본 입력', { catalogFixtureOnly: true });

  // Purchase recording is manual here: no external checkout or real order is issued.
  await page.goto(`${web}/lowest-price/${setup.ingredientId}`);
  await page.getByTestId('purchase-open').click();
  await page.getByTestId('product-name').fill('시연용 배추 2kg 포장');
  await page.getByTestId('purchase-quantity').fill('1');
  await page.getByTestId('purchase-unit').selectOption('포장');
  await page.getByTestId('package-size').fill('2');
  await page.getByTestId('package-unit').selectOption('kg');
  await page.getByTestId('purchase-price').fill('12000');
  await page.getByTestId('purchase-supplier').fill('E2E 시연 구매기록');
  await page.getByTestId('source-url').fill('https://example.com/qa-no-order');
  const beforePurchase = await stock();
  const batchesBeforePurchase = mutationPaths.filter(p => p === '/inventory/batches').length;
  const order = await uiPost('/purchase-orders', () => page.getByTestId('purchase-confirm').click());
  assert.equal(order.deliveryStatus, 'WAITING');
  assert.equal(Number(order.expectedQuantityBase), 2000);
  assert.equal(Number(order.totalAmount), 12000);
  assert.equal(order.productName, '시연용 배추 2kg 포장');
  assert.equal(await stock(), beforePurchase);
  assert.equal(mutationPaths.filter(p => p === '/inventory/batches').length, batchesBeforePurchase);
  await page.waitForURL(url => url.pathname === '/order' && url.searchParams.get('tab') === 'history');
  await page.getByTestId(`purchase-${order.id}`).waitFor();
  await screenshot('06-purchase-waiting');
  pass('포장 구매 기록 → 배송 대기·상품명 저장·재고 유지', {
    purchaseOrderId: order.id, expectedQuantityBase: 2000, stockBefore: beforePurchase, stockAfter: await stock(),
  });

  await page.goto(`${web}/order?tab=history`);
  const purchaseCard = page.getByTestId(`purchase-${order.id}`);
  await purchaseCard.getByRole('button', { name: '수령 후 입고', exact: true }).click();
  await page.getByLabel('입고일', { exact: true }).fill(setup.businessDate);
  await page.getByLabel('유통기한', { exact: true }).fill(future(setup.businessDate, 10));
  const received = await uiPost(`/purchase-orders/${order.id}/receive`, () => page.getByTestId('receive-confirm').click());
  assert.equal(received.deliveryStatus, 'RECEIVED');
  assert.equal(await stock(), beforePurchase + 2000);
  await purchaseCard.getByRole('button', { name: '수령 후 입고', exact: true }).waitFor({ state: 'hidden' });
  await screenshot('07-received');
  pass('배송 수령 후 입고 → 재고 2,000g 증가·입고 버튼 제거', { stockBefore: beforePurchase, stockAfter: await stock() });
  const repeated = await fetch(`${api}/purchase-orders/${order.id}/receive`, {
    method: 'POST', headers: { Authorization: `Bearer ${bearer}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ receivedQuantity: 2000, inboundDate: setup.businessDate, expirationDate: future(setup.businessDate, 10) }),
  });
  assert.equal(repeated.status, 409);
  assert.equal(await stock(), beforePurchase + 2000);
  pass('서버에서도 중복 입고 409·재고 보존');

  const opening = await request('POST', `/demo/opening-trigger?businessDate=${future(setup.businessDate, 1)}`, {});
  const twice = await request('POST', `/demo/opening-trigger?businessDate=${future(setup.businessDate, 1)}`, {});
  assert.equal(opening.notificationId, twice.notificationId);
  const detail = await request('GET', `/closing/notifications/${opening.notificationId}`);
  evidence.notificationDeliveryStatus = detail.deliveryStatus;
  await page.goto(`${web}/notifications/${opening.notificationId}`);
  await page.getByText(/영업 전/).first().waitFor();
  await screenshot('08-notification-detail');
  pass('영업 전 알림 저장·중복 트리거 방지·당시 판단 상세 화면', { deliveryStatus: detail.deliveryStatus });

  // A separate real account verifies the no-POS-data state without changing demo data.
  const unknownUser = { username: `qa_owner_${Date.now()}`, password: 'qaOwnerOnly1234', ownerName: '검증용 점주' };
  secrets.push(unknownUser.password);
  await request('POST', '/api/users/signup', unknownUser);
  const freshContext = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'ko-KR' });
  page = await freshContext.newPage();
  page.setDefaultTimeout(15000);
  page.on('pageerror', error => browserErrors.push(error.message));
  await login(unknownUser.username, unknownUser.password);
  await page.goto(`${web}/order`);
  await page.getByText(/판매 데이터가 없어 재고 소진을 예측할 수 없습니다/).first().waitFor();
  assert.ok(!await page.getByText('발주가 필요한 재료가 없었습니다', { exact: true }).isVisible());
  await screenshot('09-unknown-data-mobile-width');
  pass('판매 자료 없는 새 점주에게 판단 불가·업로드 안내 표시');

  const otherAuth = await request('POST', '/api/users/login', { username: unknownUser.username, password: unknownUser.password });
  secrets.push(otherAuth.accessToken, otherAuth.refreshToken);
  for (const [endpoint, expectedStatus] of [[`/ingredients/${setup.ingredientId}/batches`, 404], [`/closing/notifications/${opening.notificationId}`, 404]]) {
    const denied = await fetch(`${api}${endpoint}`, { headers: { Authorization: `Bearer ${otherAuth.accessToken}` } });
    assert.equal(denied.status, expectedStatus, `Cross-owner access must be rejected: ${endpoint}`);
  }
  pass('다른 점주의 재고 배치·알림 상세 조회 거절', { inventoryStatus: 404, notificationStatus: 404 });

  // Real onboarding creates this owner's ingredients; stock and recipes use the UI.
  const otherOptions = { token: otherAuth.accessToken };
  const onboarded = await request('POST', '/api/users/onboard', { categories: ['KOREAN'] }, otherOptions);
  assert.ok(onboarded.createdMenus > 0, 'Onboarding must supply real recipe templates');
  const ownRecommendations = await request('GET', `/closing/recommendations?businessDate=${setup.businessDate}`, undefined, otherOptions);
  const ownIngredient = ownRecommendations.items.find(item => item.baseUnit === 'g');
  assert.ok(ownIngredient, 'New owner must have an ingredient for recipe configuration');
  await page.goto(`${web}/inventory`);
  await page.getByRole('button', { name: '+ 재고 입고', exact: true }).click();
  const stockForm = page.locator('form');
  await stockForm.getByRole('combobox').selectOption(String(ownIngredient.ingredientId));
  await stockForm.getByPlaceholder('10', { exact: true }).fill('1000');
  await stockForm.getByPlaceholder('(선택)', { exact: true }).fill('2.25');
  await stockForm.locator('input[type="date"]').nth(0).fill(setup.businessDate);
  await stockForm.locator('input[type="date"]').nth(1).fill(future(setup.businessDate, 10));
  const [stockRequest, initialBatch] = await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/inventory/batches' && request.method() === 'POST'),
    uiPost('/inventory/batches', () => stockForm.getByRole('button', { name: '입고 등록', exact: true }).click(), 201),
  ]);
  assert.equal(Number(initialBatch.quantity), 1000);
  assert.equal(stockRequest.postDataJSON().costPerUnit, 2.25);
  await stockForm.getByRole('button', { name: '입고 등록', exact: true }).waitFor({ state: 'hidden' });
  pass('새 점주 초기 재고 1,000g 화면 등록·소수 단가 2.25 전송');

  await page.goto(`${web}/operations`);
  await page.getByTestId('menu-new').click();
  await page.getByTestId('menu-name').fill('QA 실제 레시피 메뉴');
  await page.getByTestId('menu-price').fill('12000');
  await page.getByTestId('menu-recipe-ingredient-0').selectOption(String(ownIngredient.ingredientId));
  await page.getByTestId('menu-recipe-quantity-0').fill('200');
  await page.getByTestId('menu-recipe-unit-0').selectOption('g');
  const newMenu = await uiPost('/menus', () => page.getByTestId('menu-save').click());
  assert.equal(Number(newMenu.recipe[0].requiredQuantity), 200);
  assert.equal(newMenu.recipe[0].ingredientId, ownIngredient.ingredientId);
  pass('새 점주의 실제 메뉴 가격·1인분 레시피 200g 화면 저장', { menuId: newMenu.menuId, setupOnly: 'onboarding via API' });

  const [templateDownload] = await Promise.all([
    page.waitForEvent('download'), page.getByTestId('pos-template').click(),
  ]);
  const ownerTemplate = path.join(output, 'owner_blank_template.xlsx');
  const ownerSales = path.join(output, 'owner_actual_sales.xlsx');
  await templateDownload.saveAs(ownerTemplate);
  // Fill the downloaded real blank template with a synthetic sale, retaining all other sheets.
  execFileSync('python3', ['-c',
    'import sys,zipfile,xml.etree.ElementTree as E\nns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"\nq=lambda t:"{"+ns+"}"+t\nwith zipfile.ZipFile(sys.argv[1]) as src, zipfile.ZipFile(sys.argv[2],"w") as dst:\n for item in src.infolist():\n  raw=src.read(item.filename)\n  if item.filename=="xl/worksheets/sheet1.xml":\n   root=E.fromstring(raw); data=root.find(q("sheetData")); assert len(data)==1,"Template must not invent sales"\n   row=E.SubElement(data,q("row"),r="2")\n   for i,value in enumerate([sys.argv[3],"",sys.argv[4],"2","24000"]):\n    cell=E.SubElement(row,q("c"),r="ABCDE"[i]+"2")\n    if i<3:\n     cell.set("t","inlineStr"); E.SubElement(E.SubElement(cell,q("is")),q("t")).text=value\n    else: E.SubElement(cell,q("v")).text=value\n   dimension=root.find(q("dimension"))\n   if dimension is not None: dimension.set("ref","A1:E2")\n   raw=E.tostring(root,encoding="utf-8",xml_declaration=True)\n  dst.writestr(item,raw)', ownerTemplate, ownerSales, setup.businessDate, newMenu.name]);
  const ownerUpload = await upload(ownerSales);
  assert.equal(ownerUpload.salesQuantity, 2);
  assert.equal(Number(ownerUpload.salesAmount), 24000);
  assert.equal(Number(ownerUpload.ingredientConsumption[0].quantity), 400);
  const ownReport = () => request('GET', `/reports/daily?businessDate=${setup.businessDate}`, undefined, otherOptions);
  const ownDaily = await ownReport();
  assert.equal(Number(ownDaily.currentInventory.find(item => item.ingredientId === ownIngredient.ingredientId).quantity), 600);
  await screenshot('10-new-owner-actual-pos');
  pass('빈 POS 양식 다운로드·실제 레시피 적용 → 판매 2·소비 400g·잔여 600g', {
    salesQuantity: ownerUpload.salesQuantity, salesAmount: Number(ownerUpload.salesAmount), consumption: Number(ownerUpload.ingredientConsumption[0].quantity), stockAfter: 600,
  });

  await page.getByTestId('menu-select').selectOption(String(newMenu.menuId));
  await page.getByTestId('menu-recipe-quantity-0').fill('300');
  const editedMenu = await uiMutation('PUT', `/menus/${newMenu.menuId}`, () => page.getByTestId('menu-save').click());
  assert.equal(Number(editedMenu.recipe[0].requiredQuantity), 300);
  const historic = await ownReport();
  assert.equal(Number(historic.ingredientConsumption[0].quantity), 400);
  assert.equal(Number(historic.currentInventory.find(item => item.ingredientId === ownIngredient.ingredientId).quantity), 600);
  await page.getByText('400 g', { exact: true }).first().waitFor();
  await screenshot('11-recipe-edited-historical-snapshot');
  pass('레시피를 300g으로 수정해도 과거 소비 기록 400g·잔여 600g 유지');

  assert.deepEqual(browserErrors, [], 'Browser runtime errors');
  pass('전체 브라우저 실행 중 런타임 예외 없음');
  evidence.completedAt = new Date().toISOString();
  evidence.result = 'PASS';
} catch (error) {
  evidence.result = 'FAIL';
  evidence.error = sanitized(error.message);
  evidence.browserErrors = browserErrors.map(sanitized);
  if (page) await screenshot('failure').catch(() => {});
  console.error(`FAIL: ${sanitized(error.message)}`);
  process.exitCode = 1;
} finally {
  await mkdir(output, { recursive: true });
  await writeFile(path.join(output, 'results.json'), `${JSON.stringify(evidence, null, 2)}\n`);
  await browser?.close();
}
