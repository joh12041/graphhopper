package com.graphhopper;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by isaac on 3/8/16.
 */
public class RoutingExample {

    // PBF from: https://mapzen.com/data/metro-extracts/
    private static final String osmFile = "./files/san-francisco-bay_california.osm.pbf";
    private static final String graphFolder = "./target/tmp/ghosm";

    private static final TranslationMap trMap = new TranslationMap().doImport();
    private static final Translation usTR = trMap.getWithFallBack(Locale.US);

    private static final float latFrom = 37.76018f;
    private static final float lonFrom = -122.42712f;
    private static final float latTo = 37.77220f;
    private static final float lonTo = -122.49171f;


    public static void main(String[] args) throws Exception {
        // create one GraphHopper instance
        System.out.println(usTR);
        GraphHopper hopper = new GraphHopper().forDesktop();
        hopper.setOSMFile(osmFile);
        // where to store graphhopper files?
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodingManager(new EncodingManager("car"));

        // now this can take minutes if it imports or a few seconds for loading
        // of course this is dependent on the area you import
        hopper.importOrLoad();

        // simple configuration of the request object, see the GraphHopperServlet classs for more possibilities.
        GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo).
                setWeighting("fastest").
                setVehicle("car").
                setLocale(Locale.US);
        GHResponse rsp = hopper.route(req);

        // first check for errors
        if(rsp.hasErrors()) {
            // handle them!
            rsp.getErrors();
            return;
        }

        // use the best path, see the GHResponse class for more possibilities.
        PathWrapper path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        PointList pointList = path.getPoints();
        double distance = path.getDistance();
        long timeInMs = path.getTime();
        System.out.println("Distance: " + distance);
        System.out.println("Time: " + timeInMs/1000/60);

        InstructionList il = path.getInstructions();
        // iterate over every turn instruction
        for(Instruction instruction : il) {
            System.out.println(instruction.getTurnDescription(usTR) + " for " + instruction.getDistance() + " meters.");
        }

        // or get the json
        List<Map<String, Object>> iList = il.createJson();
        System.out.println("JSON: " + iList);

        // or get the result as gpx entries:
        List<GPXEntry> list = il.createGPXList();
        System.out.println("GPX: " + list);
    }
}
