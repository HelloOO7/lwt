package cz.spojenka.lwt.cicomock.services;

import cz.pid.jrxml.DAVKAJR;
import cz.pid.jrxml.Tablo;
import cz.pid.jrxml.Zastavka;
import cz.spojenka.lwt.cicomock.model.RopidStopInfo;
import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.JAXB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class StopDataService {

    private final Logger logger = LoggerFactory.getLogger(StopDataService.class);

    @Value("${mock.data.ropid-stops}")
    private String ropidStopsPath;

    private final Map<String, RopidStopInfo> stopInfo = new HashMap<>();

    @PostConstruct
    public void loadData() {
        logger.info("Loading stops XML " + ropidStopsPath);
        DAVKAJR davka = JAXB.unmarshal(new File(ropidStopsPath), DAVKAJR.class);
        Map<String, Zastavka> zastavky = mapElements(davka.getZ(), Zastavka::getU, Zastavka::getZ);
        Map<String, Tablo> tabla = mapElements(davka.getT(), Tablo::getU, Tablo::getZ);
        for (var aswId : zastavky.keySet()) {
            Zastavka z = zastavky.get(aswId);
            Tablo t = tabla.get(aswId);
            if (z != null && t != null) {
                stopInfo.put(aswId, new RopidStopInfo(aswId, t.getCtn(), t.getLcdn(), z.getRdisp()));
            }
        }
        logger.info("Loaded " + stopInfo.size() + " stops from XML");
    }

    private <T> Map<String, T> mapElements(Collection<T> elements, Function<T, Short> getAswNode, Function<T, Short> getAswStop) {
        Map<String, T> map = new HashMap<>();
        for (T element : elements) {
            Short aswNode = getAswNode.apply(element);
            Short aswStop = getAswStop.apply(element);
            if (aswNode != null && aswStop != null) {
                map.put(aswNode + "/" + aswStop, element);
            }
        }
        return map;
    }

    public RopidStopInfo getStopInfo(String aswId) {
        return stopInfo.get(aswId);
    }
}
