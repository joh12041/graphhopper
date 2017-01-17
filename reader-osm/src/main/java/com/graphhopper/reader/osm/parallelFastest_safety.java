package com.graphhopper.reader.osm;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by isaac on 09/14/16.
 */
public class parallelFastest_safety {

    String city;
    String route_type;
    HashMap<String, FileWriter> outputFiles;
    private String osmFile = "./reader-osm/files/";
    private String graphFolder = "./reader-osm/target/tmp/";
    private String inputPointsFN = "../data/intermediate/";
    private String outputPointsFN = "../data/final/impacts/";
    private String gvfnStem = "../data/intermediate/";
    private String gctfnStem = "../geometries/";
    private ArrayList<String> gridValuesFNs = new ArrayList<>();
    private ArrayList<String> gridCTsFNs = new ArrayList<>();
    private HashMap<String, Integer> gvHeaderMap;
    private HashMap<String, Float> gridBeauty;
    private HashMap<String, Integer> gridCT;
    private GraphHopper hopper;
    private MapMatching mapMatching;
    private String outputheader = "ID,name,polyline_points,total_time_in_sec,total_distance_in_meters,number_of_steps,maneuvers,beauty,simplicity,pctNonHighwayTime,pctNonHighwayDist,pctNeiTime,pctNeiDist" +
            System.getProperty("line.separator");
    private ArrayList<float[]> inputPoints = new ArrayList<>();
    private ArrayList<String> id_to_points = new ArrayList<>();
    private ArrayList<String> optimizations = new ArrayList<>();
    private String bannedGridCellsFn;


    public parallelFastest_safety(String city, String route_type) {

        this.city = city;
        this.route_type = route_type;
        this.outputFiles = new HashMap<>();
        optimizations.add("safety");
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setRouteType(String route_type) {
        this.route_type = route_type;
    }

    public PathWrapper GPXToPath(ArrayList<GPXEntry> gpxEntries) {
        PathWrapper matchGHRsp = new PathWrapper();
        try {
            MatchResult mr = mapMatching.doWork(gpxEntries);
            Path path = mapMatching.calcPath(mr);
            new PathMerger().doWork(matchGHRsp, Collections.singletonList(path), new TranslationMap().doImport().getWithFallBack(Locale.US));
        }
        catch (RuntimeException e) {
            System.out.println("Broken GPX trace.");
            System.out.println(e.getMessage());
        }
        return matchGHRsp;
    }


    public void setDataSources() throws Exception {
        if (city.equals("sf")) {
            osmFile = osmFile + "san-francisco-bay_california.osm.pbf";
            graphFolder = graphFolder + "ghosm_sf_noch";
            inputPointsFN = inputPointsFN + "sf_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "sf_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "06075_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "06075_ct_grid.csv");
            bannedGridCellsFn = gctfnStem + "06075_banned_grid_cells.csv";
        } else if (city.equals("nyc")) {
            osmFile = osmFile + "new-york_new-york.osm.pbf";
            graphFolder = graphFolder + "ghosm_nyc_noch";
            inputPointsFN = inputPointsFN + "nyc_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "nyc_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "36005_logfractionempath_ft.csv");
            gridValuesFNs.add(gvfnStem + "36047_logfractionempath_ft.csv");
            gridValuesFNs.add(gvfnStem + "36061_logfractionempath_ft.csv");
            gridValuesFNs.add(gvfnStem + "36081_logfractionempath_ft.csv");
            gridValuesFNs.add(gvfnStem + "36085_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "nyc_ct_grid.csv");
            bannedGridCellsFn = gctfnStem + "nyc_banned_grid_cells.csv";
        } else if (city.equals("bos")) {
            osmFile = osmFile + "boston_massachusetts.osm.pbf";
            graphFolder = graphFolder + "ghosm_bos_noch";
            inputPointsFN = inputPointsFN + "bos_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "bos_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "25025_beauty_twitter.csv");
            gridCTsFNs.add(gctfnStem + "25025_ct_grid.csv");
            bannedGridCellsFn = "";
        } else if (city.equals("chi")) {
            osmFile = osmFile + "chicago_illinois.osm.pbf";
            graphFolder = graphFolder + "ghosm_chi_noch";
            inputPointsFN = inputPointsFN + "chi_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "chi_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "17031_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "17031_ct_grid.csv");
            bannedGridCellsFn = "";
        } else if (city.equals("sin")) {
            osmFile = osmFile + "singapore.osm.pbf";
            graphFolder = graphFolder + "ghosm_sin_noch";
            inputPointsFN = inputPointsFN + "sin_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "sin_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "SINGAPORE_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "");
            bannedGridCellsFn = "";
        } else if (city.equals("lon")) {
            osmFile = osmFile + "london_england.osm.pbf";
            graphFolder = graphFolder + "ghosm_lon_noch";
            inputPointsFN = inputPointsFN + "lon_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "lon_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "LONDON_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "");
            bannedGridCellsFn = "";
        } else if (city.equals("man")) {
            osmFile = osmFile + "manila_philippines.osm.pbf";
            graphFolder = graphFolder + "ghosm_man_noch";
            inputPointsFN = inputPointsFN + "man_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "man_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "MANILA_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "");
            bannedGridCellsFn = "";
        } else {
            throw new Exception("Invalid Parameters: city must be of 'SF','NYC', or 'BOS' and route_type of 'grid' or 'rand'");
        }
    }

    public void getGridValues() throws Exception {
        gvHeaderMap = new HashMap<>();
        gridBeauty = new HashMap<>();

        for (String fn : gridValuesFNs) {
            try {
                Scanner sc_in = new Scanner(new File(fn));
                String[] gvHeader = sc_in.nextLine().split(",");
                int i = 0;
                for (String col : gvHeader) {
                    gvHeaderMap.put(col, i);
                    i++;
                }
                String line;
                String[] vals;
                String rc;
                float beauty;
                while (sc_in.hasNext()) {
                    line = sc_in.nextLine();
                    vals = line.split(",");
                    try {
                        rc = vals[gvHeaderMap.get("rid")] + "," + vals[gvHeaderMap.get("cid")];
                        beauty = Float.valueOf(vals[gvHeaderMap.get("beauty")]);
                        gridBeauty.put(rc, beauty);
                    } catch (NullPointerException ex) {
                        System.out.println(ex.getMessage());
                        System.out.println(line);
                        continue;
                    }
                }
            } catch (IOException io) {
                System.out.println(io + ": " + fn + " does not exist.");
            }
        }
    }


    public void prepareGraphHopper() {
        // create one GraphHopper instance
        hopper = new GraphHopperOSM().forDesktop().setCHEnabled(false);
        hopper.setDataReaderFile(osmFile);
        // where to store graphhopper files?
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodingManager(new EncodingManager("car"));
        hopper.setBannedGridCellsFn(bannedGridCellsFn);

        // now this can take minutes if it imports or a few seconds for loading
        // of course this is dependent on the area you import
        hopper.importOrLoad();
    }


    public String writeOutput(int i, String optimized, String name, String od_id, PathWrapper bestPath, float score) {

        // points, distance in meters and time in seconds (convert from ms) of the full path
        PointList pointList = bestPath.getPoints();
        int simplicity = bestPath.getSimplicity();
        double distance = Math.round(bestPath.getDistance() * 100) / 100;
        double nonHighwayDistance = bestPath.getNonHighwayDistance();
        double smallNeiDistance = bestPath.getNeiHighwayDistance();
        double pctNHD = Math.round(1000.0 * (float) nonHighwayDistance / distance) / 1000.0;
        double pctNeiD = Math.round(1000.0 * (float) smallNeiDistance / distance) / 1000.0;
        long timeInSec = bestPath.getTime() / 1000;
        long nonHighwayTimeInSec = bestPath.getNonHighwayTime() / 1000;
        long neiHighwayTimeInSec = bestPath.getTimeSmallNeigh() / 1000;
        double pctNHT = Math.round(1000.0 * (float) nonHighwayTimeInSec / timeInSec) / 1000.0;
        double pctNeiT = Math.round(1000.0 * (float) neiHighwayTimeInSec / timeInSec) / 1000.0;
        InstructionList il = bestPath.getInstructions();
        int numDirections = il.getSize();
        // iterate over every turn instruction
        ArrayList<String> maneuvers = new ArrayList<>();
        for (Instruction instruction : il) {
            maneuvers.add(instruction.getSimpleTurnDescription());
        }

        System.out.println(i + " (" + optimized + "): Distance: " + distance + "m;\tTime: " + timeInSec + "sec;\t# Directions: " + numDirections + ";\tSimplicity: " + simplicity + ";\tScore: " + score + ";\tPctNHT: " + pctNHT + ";\tPctNeiT: " + pctNeiT);
        return od_id + "," + name + "," +
                "\"[" + pointList + "]\"," +
                timeInSec + "," + distance + "," + numDirections +
                ",\"" + maneuvers.toString() + "\"" + "," +
                score + "," + simplicity + "," +
                pctNHT + "," + pctNHD + "," +
                pctNeiT + "," + pctNeiD +
                System.getProperty("line.separator");
    }


    public void setODPairs() throws Exception {
        // Prep Filewriters (Optimized, Worst-but-same-distance, Fastest, Simplest)
        for (String optimization : optimizations) {
            outputFiles.put(optimization, new FileWriter(outputPointsFN.replaceFirst(".csv", "_" + optimization + ".csv"), true));
        }

        for (FileWriter fw : outputFiles.values()) {
            fw.write(outputheader);
        }

        // Bring in origin-destination pairs for processing
        Scanner sc_in = new Scanner(new File(inputPointsFN));
        String header = sc_in.nextLine();
        String od_id;
        float laF;
        float loF;
        float laT;
        float loT;
        float idx = 0;
        System.out.println("Input data points header: " + header);
        while (sc_in.hasNext()) {
            idx = idx + 1;
            String line = sc_in.nextLine();
            String[] vals = line.split(",");
            od_id = vals[0];
            loF = Float.valueOf(vals[1]);
            laF = Float.valueOf(vals[2]);
            loT = Float.valueOf(vals[3]);
            laT = Float.valueOf(vals[4]);
            inputPoints.add(new float[]{laF, loF, laT, loT, idx});
            id_to_points.add(od_id);
        }
        int numPairs = inputPoints.size();
        System.out.println(numPairs + " origin-destination pairs.");

    }


    public float getBeauty(PathWrapper path) {
        HashSet<String> roundedPoints = path.roundPoints();
        float score = 0;
        for (String pt : roundedPoints) {
            if (gridBeauty.containsKey(pt)) {
                score = score + gridBeauty.get(pt);
            }
        }
        score = score / roundedPoints.size();
        return score;
    }


    public void process_routes() throws Exception {

        AtomicInteger num_processed = new AtomicInteger();
        int num_odpairs = id_to_points.size();

        ConcurrentHashMap<String, ConcurrentHashMap<String, String>> results = new ConcurrentHashMap<>();
        for (String optimization : optimizations) {
            results.put(optimization, new ConcurrentHashMap<>());
        }

        // initialize banned edges
        GHRequest req = new GHRequest(inputPoints.get(0)[0], inputPoints.get(0)[1], inputPoints.get(0)[2], inputPoints.get(0)[3]).  // latFrom, lonFrom, latTo, lonTo
                setWeighting("safest_fastest").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("dijkstrabi");
        GHResponse rsp = hopper.route(req);

        id_to_points.parallelStream().forEach(od_id -> {
            System.out.println("Processing: " + od_id);
            int route = id_to_points.indexOf(od_id);
            HashMap<String, String> routes = process_route(route);
            for (String optimization : optimizations) {
                results.get(optimization).put(od_id, routes.getOrDefault(optimization, "FAILURE"));
            }
            int i = num_processed.incrementAndGet();
            if (i % 50 == 0) {
                System.out.println(System.getProperty("line.separator") + i + " of " + num_odpairs + " o-d pairs processed." + System.getProperty("line.separator"));
            }
        }
        );

        for (String optimization : optimizations) {
            for (String result : results.get(optimization).values()) {
                outputFiles.get(optimization).write(result);
            }
            outputFiles.get(optimization).close();
        }
    }


    public HashMap<String, String> process_route(int route) {
        // Loop through origin-destination pairs, processing each one for beauty, non-beautiful matched, fastest, and simplest
        float[] points;
        String od_id;
        HashMap<String, String> responses = new HashMap<>();

        // Get Routes
        points = inputPoints.get(route);
        od_id = id_to_points.get(route);
        GHRequest req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                setWeighting("safest_fastest").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("dijkstrabi");
        GHResponse rsp = hopper.route(req);

        String defaultRow = od_id + ",main," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                + ")]\"," + "-1,-1,-1,[],-1,-1,-1,-1,-1,-1" + System.getProperty("line.separator");

        // first check for errors
        if (rsp.hasErrors()) {
            // handle them!
            System.out.println(rsp.getErrors().toString());
            System.out.println(route + ": Error - skipping.");
            for (String optimization : optimizations) {
                responses.put(optimization, defaultRow);
            }
            return responses;
        }

        // Get All Routes (up to 10K right now)
        List<PathWrapper> paths = rsp.getAll();

        if (paths.size() == 0) {
            System.out.println(route + ": No paths - skipping.");
            for (String optimization : optimizations) {
                responses.put(optimization, defaultRow);
            }
            return responses;
        }

        // Fastest Safest Route
        PathWrapper bestPath = paths.get(0);
        responses.put("safety", writeOutput(route, "Safe", "safe-fastest", od_id, bestPath, getBeauty(bestPath)));

        return responses;
    }

    public static void main(String[] args) throws Exception {

        // PBFs from: https://mapzen.com/data/metro-extracts/

        String city = "nyc";  // sf, nyc, chi, lon, man, sin
        String odtype = "grid";  // grid, rand
        //System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "12");
        //String city = args[0];
        //String odtype = args[1];
        parallelFastest_safety ksp = new parallelFastest_safety(city, odtype);

        ksp.setDataSources();
        ksp.getGridValues();
        ksp.prepareGraphHopper();
        ksp.setODPairs();
        ksp.process_routes();  // get Graphhopper routes
    }
}
