package com.naengjang_goat.inventory_system.batch.reader;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.batch.service.KamisApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class KamisApiReader implements ItemReader<KamisPriceDto> {

    private final KamisApiClient kamisApiClient;
    private Iterator<KamisPriceDto> iterator;

    @Override
    public KamisPriceDto read() throws Exception {
        if (iterator == null) {
            String xml = kamisApiClient.fetchXml();
            List<KamisPriceDto> list = parse(xml);
            iterator = list.iterator();
            log.info("[KAMIS-READER] Loaded {} items", list.size());
        }

        return iterator.hasNext() ? iterator.next() : null;
    }

    private List<KamisPriceDto> parse(String xml) throws Exception {
        List<KamisPriceDto> result = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        NodeList dataNodes = doc.getElementsByTagName("data");
        if (dataNodes.getLength() == 0) return result;

        Element data = (Element) dataNodes.item(0);
        String error = getTagText(data, "error_code");
        if (!"000".equals(error)) return result;

        NodeList items = data.getElementsByTagName("item");

        for (int i = 0; i < items.getLength(); i++) {
            Element e = (Element) items.item(i);

            KamisPriceDto dto = new KamisPriceDto();
            dto.setItemCode(getTagText(e, "item_code"));
            dto.setProductName(getTagText(e, "item_name"));
            dto.setUnit(getTagText(e, "unit"));
            dto.setDpr1(normalize(getTagText(e, "dpr1")));
            dto.setDpr4(normalize(getTagText(e, "dpr4")));

            result.add(dto);
        }

        return result;
    }

    private String getTagText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent().trim();
    }

    private String normalize(String v) {
        if (v == null || v.isBlank() || "-".equals(v)) return null;
        return v.replace(",", "").trim();
    }
}
