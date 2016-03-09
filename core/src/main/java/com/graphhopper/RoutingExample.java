package com.graphhopper;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.*;

import java.lang.reflect.Array;
import java.util.*;

import java.io.*;

/**
 * Created by isaac on 3/8/16.
 */
public class RoutingExample {

    // PBF from: https://mapzen.com/data/metro-extracts/
    private static final String osmFile = "./files/san-francisco-bay_california.osm.pbf";
    private static final String graphFolder = "./target/tmp/ghosm";

    private static final TranslationMap trMap = new TranslationMap().doImport();
    private static final Translation usTR = trMap.getWithFallBack(Locale.US);

    private static final float exlatFrom = 37.76018f;
    private static final float exlonFrom = -122.42712f;
    private static final float exlatTo = 37.77220f;
    private static final float exlonTo = -122.49171f;


    public static void main(String[] args) throws Exception {
        // create one GraphHopper instance
        System.out.println(usTR);
        GraphHopper hopper = new GraphHopper().forDesktop();
        hopper.setOSMFile(osmFile);
        // where to store graphhopper files?
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodingManager(new EncodingManager("car"));

        String inputPointsFN = "../../data/sample_origin_destination_sanfran.csv";
        ArrayList<float[]> inputPoints = new ArrayList<float[]>();
        Scanner sc = new Scanner(new File(inputPointsFN));
        String header = sc.nextLine();
        float laF;
        float loF;
        float laT;
        float loT;
        System.out.println("Input data points header: " + header);
        while (sc.hasNext()) {
            String line = sc.nextLine();
            String[] vals = line.split(",");
            loF = Float.valueOf(vals[0]);
            laF = Float.valueOf(vals[1]);
            loT = Float.valueOf(vals[2]);
            laT = Float.valueOf(vals[3]);
            inputPoints.add(new float[] {laF, loF, laT, loT});
        }
        int numPairs = inputPoints.size();
        System.out.println(numPairs + " origin-destination pairs.");

        // now this can take minutes if it imports or a few seconds for loading
        // of course this is dependent on the area you import
        hopper.importOrLoad();

        // simple configuration of the request object, see the GraphHopperServlet classs for more possibilities.
        float[] points;
        List<Map<String, Object>> iList = null;
        for (int i=0; i<numPairs; i++) {
            points = inputPoints.get(i);
            GHRequest req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                    setWeighting("fastest").
                    setVehicle("car").
                    setLocale(Locale.US);
            GHResponse rsp = hopper.route(req);

            // first check for errors
            if (rsp.hasErrors()) {
                // handle them!
                rsp.getErrors();
                return;
            }

            // use the best path, see the GHResponse class for more possibilities.
            PathWrapper path = rsp.getBest();

            // points, distance in meters and time in millis of the full path
            PointList pointList = path.getPoints();
            double distance = Math.round(path.getDistance()*100) / 100;
            long timeInMs = path.getTime();
            InstructionList il = path.getInstructions();
            int numDirections = il.getSize();
            System.out.println(i + ": Distance: " + distance + "m;\tTime: " + timeInMs / 1000 / 60 + "min;\t# Directions: " + numDirections);

            // iterate over every turn instruction
            //for(Instruction instruction : il) {
            //    System.out.println(instruction.getTurnDescription(usTR) + " for " + instruction.getDistance() + " meters.");
            //}

            // or get the json
            iList = il.createJson();
            //System.out.println("JSON: " + iList);

            // or get the result as gpx entries:
            //List<GPXEntry> list = il.createGPXList();
            //System.out.println("GPX: " + list);
        }
        // example JSON
        System.out.println("Example JSON: " + iList);
    }
}
