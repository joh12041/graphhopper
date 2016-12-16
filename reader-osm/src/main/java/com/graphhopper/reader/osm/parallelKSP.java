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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Created by isaac on 09/14/16.
 */
public class parallelKSP {

    String city;
    String route_type;
    HashMap<String, FileWriter> outputFiles;
    private String osmFile = "./reader-osm/files/";
    private String graphFolder = "./reader-osm/target/tmp/";
    private String inputPointsFN = "../data/intermediate/";
    private String outputPointsFN = "../data/testing/";
    private String gvfnStem = "../data/intermediate/";
    private String gctfnStem = "../geometries/";
    private ArrayList<String> gridValuesFNs = new ArrayList<>();
    private ArrayList<String> gridCTsFNs = new ArrayList<>();
    private HashMap<String, Integer> gvHeaderMap;
    private HashMap<String, Float> gridBeauty;
    private HashMap<String, Integer> gridCT;
    private GraphHopper hopper;
    private MapMatching mapMatching;
    private String outputheader = "ID,name,polyline_points,total_time_in_sec,total_distance_in_meters,number_of_steps,maneuvers,beauty,simplicity,numCTs" +
            System.getProperty("line.separator");
    private ArrayList<float[]> inputPoints = new ArrayList<>();
    private ArrayList<String> id_to_points = new ArrayList<>();
    private ArrayList<String> optimizations = new ArrayList<>();


    public parallelKSP(String city, String route_type) {

        this.city = city;
        this.route_type = route_type;
        this.outputFiles = new HashMap<>();
        optimizations.add("beauty");
        optimizations.add("ugly");
        optimizations.add("simple");
        optimizations.add("besi");
        optimizations.add("fast");
        optimizations.add("short");
        optimizations.add("alt");

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

    public void PointsToPath(String fin, String fout) throws IOException {
        Scanner sc_in = new Scanner(new File(fin));
        String[] pointsHeader = sc_in.nextLine().split(",");
        int idIdx = -1;
        int nameIdx = -1;
        int latIdx = -1;
        int lonIdx = -1;
        int timeIdx = -1;
        for (int i=0; i<pointsHeader.length; i++) {
            if (pointsHeader[i].equalsIgnoreCase("ID")) {
                idIdx = i;
            }
            else if (pointsHeader[i].equalsIgnoreCase("name")) {
                nameIdx = i;
            }
            else if (pointsHeader[i].equalsIgnoreCase("lat")) {
                latIdx = i;
            }
            else if (pointsHeader[i].equalsIgnoreCase("lon")) {
                lonIdx = i;
            }
            else if (pointsHeader[i].equalsIgnoreCase("millis")) {
                timeIdx = i;
            }
            else {
                System.out.println("Unexpected header value: " + pointsHeader[i]);
            }
        }
        String optimized = "";
        if (fin.indexOf("google") > -1) {
            optimized = optimized + "Goog";
        } else if (fin.indexOf("mapquest") > -1) {
            optimized = optimized + "MapQ";
        } else {
            System.out.println("Don't recognize platform: " + fin);
        }
        if (fin.indexOf("alt") > -1) {
            optimized = optimized + " altn";
        } else if (fin.indexOf("main") > -1) {
            optimized = optimized + " main";
        } else {
            System.out.println("Don't recognize route type: " + fin);
        }
        String line;
        String[] vals;
        String routeID = "";
        String prevRouteID = "";
        String name = "";
        String prevName = "";
        String label = "";
        String prevLabel = "";
        double lat;
        double lon;
        long time;
        ArrayList<GPXEntry> pointsList = new ArrayList<>();
        PathWrapper path;
        FileWriter sc_out = new FileWriter(fout, true);
        sc_out.write(outputheader);
        int i = 0;
        float score;
        while (sc_in.hasNext()) {
            line = sc_in.nextLine();
            vals = line.split(",");
            routeID = vals[idIdx];
            name = vals[nameIdx];
            if (name.equalsIgnoreCase("alternative 2") || name.equalsIgnoreCase("alternative 3")) {
                continue;
            }
            lat = Double.valueOf(vals[latIdx]);
            lon = Double.valueOf(vals[lonIdx]);
            time = Long.valueOf(vals[timeIdx]);
            label = routeID + "|" + name;
            GPXEntry pt = new GPXEntry(lat, lon, time);
            if (label.equalsIgnoreCase(prevLabel)) {
                pointsList.add(pt);
            }
            else if (pointsList.size() > 0) {
                path = GPXToPath(pointsList);
                if (path.getDistance() > 0) {
                    score = getBeauty(path);
                    //writeOutput(sc_out, i, optimized, prevName, prevRouteID, path, score, getNumCTs(path));
                }
                pointsList.clear();
                pointsList.add(pt);
                i++;
                if (i % 10 == 0) {
                    for (FileWriter fw : outputFiles.values()) {
                        fw.flush();
                    }
                }
            } else {
                System.out.println("First point.");
                pointsList.add(pt);
            }
            prevRouteID = routeID;
            prevName = name;
            prevLabel = label;
        }
        if (pointsList.size() > 0) {
            path = GPXToPath(pointsList);
            if (path.getDistance() > 0) {
                score = getBeauty(path);
                //writeOutput(sc_out, i, optimized, prevName, prevRouteID, path, score, getNumCTs(path));
            }
        }
        sc_out.close();
        sc_in.close();
    }

    //TODO: find some way to match path to virtual nodes at start/finish or hope map-matcher updates
    public PathWrapper trimPath(PathWrapper path, ArrayList<GPXEntry> original) {
        return new PathWrapper();
    }


    public void setDataSources() throws Exception {
        if (city.equals("sf")) {
            osmFile = osmFile + "san-francisco-bay_california.osm.pbf";
            graphFolder = graphFolder + "ghosm_sf_noch";
            inputPointsFN = inputPointsFN + "sf_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "sf_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "06075_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "06075_ct_grid.csv");
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
        } else if (city.equals("bos")) {
            osmFile = osmFile + "boston_massachusetts.osm.pbf";
            graphFolder = graphFolder + "ghosm_bos_noch";
            inputPointsFN = inputPointsFN + "bos_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "bos_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "25025_beauty_twitter.csv");
            gridCTsFNs.add(gctfnStem + "25025_ct_grid.csv");
        } else {
            throw new Exception("Invalid Parameters: city must be of 'SF','NYC', or 'BOS' and route_type of 'grid' or 'rand'");
        }
    }

    public void getGridValues() throws Exception {
        gvHeaderMap = new HashMap<>();
        gridBeauty = new HashMap<>();

        for (String fn : gridValuesFNs) {
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

        }
    }

    public void getGridCTs() throws Exception {
        gridCT = new HashMap<>();
        for (String fn : gridCTsFNs) {
            Scanner sc_in = new Scanner(new File(fn));
            sc_in.nextLine();
            String line;
            String[] vals;
            String rc;
            int ct;
            while (sc_in.hasNext()) {
                line = sc_in.nextLine();
                vals = line.split(",");
                try {
                    rc = vals[1] + "," + vals[0];
                    ct = Integer.valueOf(vals[2]);
                    gridCT.put(rc, ct);
                } catch (NullPointerException ex) {
                    System.out.println(ex.getMessage());
                    System.out.println(line);
                    continue;
                }
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

        // now this can take minutes if it imports or a few seconds for loading
        // of course this is dependent on the area you import
        hopper.importOrLoad();
    }

    public void prepMapMatcher() {

        // create MapMatching object, can and should be shared accross threads
        AlgorithmOptions algoOpts = AlgorithmOptions.start().
                algorithm(Parameters.Algorithms.DIJKSTRA).
                traversalMode(hopper.getTraversalMode()).
                hints(new HintsMap().put("weighting", "fastest").put("vehicle", "car")).
                build();
        mapMatching = new MapMatching(hopper, algoOpts);
        mapMatching.setTransitionProbabilityBeta(0.00959442);
//        mapMatching.setTransitionProbabilityBeta(0.000959442);
        mapMatching.setMeasurementErrorSigma(100);
    }


    public String writeOutput(int i, String optimized, String name, String od_id, PathWrapper bestPath, float score, int numCTs) {

        // points, distance in meters and time in seconds (convert from ms) of the full path
        PointList pointList = bestPath.getPoints();
        int simplicity = bestPath.getSimplicity();
        double distance = Math.round(bestPath.getDistance() * 100) / 100;
        long timeInSec = bestPath.getTime() / 1000;
        InstructionList il = bestPath.getInstructions();
        int numDirections = il.getSize();
        // iterate over every turn instruction
        ArrayList<String> maneuvers = new ArrayList<>();
        for (Instruction instruction : il) {
            maneuvers.add(instruction.getSimpleTurnDescription());
        }

        System.out.println(i + " (" + optimized + "): Distance: " + distance + "m;\tTime: " + timeInSec + "sec;\t# Directions: " + numDirections + ";\tSimplicity: " + simplicity + ";\tScore: " + score + ";\tNumCts: " + numCTs);
        return od_id + "," + name + "," + "\"[" + pointList + "]\"," + timeInSec + "," + distance + "," + numDirections +
                ",\"" + maneuvers.toString() + "\"" + "," + score + "," + simplicity + "," + numCTs + System.getProperty("line.separator");


    }

    public int getNumCTs(PathWrapper path) {
        HashSet<String> roundedPoints = path.roundPoints();
        HashSet<Integer> cts = new HashSet<>();
        for (String pt : roundedPoints) {
            if (gridCT.containsKey(pt)) {
                cts.add(gridCT.get(pt));
            }
        }
        return cts.size();
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

    public void process_routes() throws Exception {

        ConcurrentHashMap<String, ConcurrentHashMap<String, String>> results = new ConcurrentHashMap<>();
        for (String optimization : optimizations) {
            results.put(optimization, new ConcurrentHashMap<>());
        }
        id_to_points.parallelStream().forEach(od_id -> {
            System.out.println("Processing: " + od_id);
            int route = id_to_points.indexOf(od_id);
            HashMap<String, String> routes = process_route(route);
            for (String optimization : optimizations) {
                results.get(optimization).put(od_id, routes.getOrDefault(optimization, "FAILURE"));
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
                setWeighting("fastest").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("ksp");
        GHResponse rsp = hopper.route(req);

        String defaultRow = od_id + ",main," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                + ")]\"," + "-1,-1,-1,[],-1,-1,-1" + System.getProperty("line.separator");

        // first check for errors
        if (rsp.hasErrors()) {
            // handle them!
            System.out.println(rsp.getErrors().toString());
            System.out.println(route + ": Skipping.");
            for (String optimization : optimizations) {
                responses.put(optimization, defaultRow);
            }
            return responses;
        }

        // Get All Routes (up to 10K right now)
        List<PathWrapper> paths = rsp.getAll();

        // Score each route on beauty to determine most beautiful
        int j = 0;
        float bestscore = -1000;
        int routeidx = -1;
        for (PathWrapper path : paths) {
            float score = getBeauty(path);
            if (score > bestscore) {
                bestscore = score;
                routeidx = j;
            }
            j++;
        }
        responses.put("beauty", writeOutput(route, "Best", "beauty", od_id, paths.get(routeidx), bestscore, getNumCTs(paths.get(routeidx))));
        float maxBeauty = bestscore;

        // Find least-beautiful route within similar distance constraints
        double beautyDistance = paths.get(routeidx).getDistance();
        j = 0;
        bestscore = 1000;
        routeidx = -1;
        double uglydistance;
        for (PathWrapper path : paths) {
            uglydistance = path.getDistance();
            if (uglydistance / beautyDistance < 1.05 && uglydistance / beautyDistance > 0.95) {
                float score = getBeauty(path);
                if (score < bestscore) {
                    bestscore = score;
                    routeidx = j;
                }
            }
            j++;
        }
        responses.put("ugly", writeOutput(route, "Wrst", "ugly", od_id, paths.get(routeidx), bestscore, getNumCTs(paths.get(routeidx))));

        // Simplest Route
        j = 0;
        bestscore = 10000;
        routeidx = 0;
        float beauty = -1;
        for (PathWrapper path : paths) {
            int score = path.getSimplicity();
            if (score < bestscore) {
                bestscore = score;
                routeidx = j;
                beauty = getBeauty(path);
            }
            j++;
        }
        responses.put("simple", writeOutput(route, "Simp", "simple", od_id, paths.get(routeidx), beauty, getNumCTs(paths.get(routeidx))));
        float minSimplicity = bestscore;

        // Fastest Route
        PathWrapper bestPath = paths.get(0);
        beauty = getBeauty(bestPath);
        responses.put("fast", writeOutput(route, "Fast", "Fastest", od_id, bestPath, beauty, getNumCTs(bestPath)));

        // Beautifully simple route
        j = 0;
        bestscore = 0;
        routeidx = 0;
        float combined;
        for (PathWrapper path : paths) {
            combined = (minSimplicity / path.getSimplicity()) + (getBeauty(path) / maxBeauty);
            if (combined > bestscore) {
                bestscore = combined;
                routeidx = j;
            }
            j++;
        }
        responses.put("besi", writeOutput(route, "BeSi", "beauty-simple", od_id, paths.get(routeidx), getBeauty(paths.get(routeidx)), getNumCTs(paths.get(routeidx))));


        // Shortest Route
        req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                setWeighting("shortest").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("dijkstrabi");
        rsp = hopper.route(req);

        // first check for errors
        if (rsp.hasErrors()) {
            // handle them!
            System.out.println(rsp.getErrors().toString());
            System.out.println(route + ": Skipping shortest path.");
            responses.put("short", defaultRow);
        } else {
            // Get shortest path
            bestPath = rsp.getBest();
            beauty = getBeauty(bestPath);
            responses.put("short", writeOutput(route, "Shrt", "shortest", od_id, bestPath, beauty, getNumCTs(bestPath)));
        }


        // Alternative Route
        req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                setWeighting("fastest").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("alternative_route");
        rsp = hopper.route(req);

        // first check for errors
        if (rsp.hasErrors()) {
            // handle them!
            System.out.println(rsp.getErrors().toString());
            responses.put("alt", defaultRow.replace("main", "alternative"));

        } else {
            // Get Alt Routes (should be 2, of which first is the fastest path)
            paths = rsp.getAll();
            if (paths.size() < 2) {
                System.out.println(route + ": Did not return an alternative path.");
                responses.put("alt", defaultRow.replace("main", "alternative"));
            } else {
                PathWrapper altpath = paths.get(1);
                beauty = getBeauty(altpath);
                responses.put("alt", writeOutput(route, "Altn", "altn", od_id, altpath, beauty, getNumCTs(altpath)));
            }
        }


        return responses;
    }

    public static void main(String[] args) throws Exception {

        // PBFs from: https://mapzen.com/data/metro-extracts/

        //String city = args[0];
        String city = "nyc";
        parallelKSP ksp = new parallelKSP(city, "grid");
        boolean matchexternal = false;
        boolean getghroutes = true;

        if (matchexternal) {
            ksp.setDataSources();
            ksp.getGridValues();
            ksp.prepareGraphHopper();
            ksp.getGridCTs();
            ksp.prepMapMatcher();  // score external API routes
            String inputfolder = "../data/intermediate/";
            String outputfolder = "../data/output/";
            ArrayList<String> platforms = new ArrayList<>();
            platforms.add("google");
            platforms.add("mapquest");
            ArrayList<String> conditions = new ArrayList<>();
            conditions.add("traffic");
            conditions.add("notraffic");
            ArrayList<String> routetypes = new ArrayList<>();
            routetypes.add("main");
            routetypes.add("alt");
            for (String platform : platforms) {
                for (String condition : conditions) {
                    for (String routetype : routetypes) {
                        ksp.PointsToPath(inputfolder + city + "_grid_" + platform + "_" + condition + "_routes_" + routetype + "_gpx.csv", outputfolder + city + "_grid_" + platform + "_" + condition + "_routes_" + routetype + "_ghenhanced_sigma100_transitionDefault.csv");
                    }
                }
            }
        }

        if (getghroutes) {
            ksp.setDataSources();
            ksp.getGridValues();
            ksp.prepareGraphHopper();
            ksp.getGridCTs();
            ksp.setODPairs();
            ksp.process_routes();  // get Graphhopper routes
        }
    }
}
