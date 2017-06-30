package nz.bywater.jenkinsci.plugins.swarmqueuelistener;

import com.jayway.jsonpath.*;
import com.jayway.jsonpath.internal.function.Parameter;
import hudson.Extension;
import hudson.init.Initializer;
import hudson.model.Computer;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import jenkins.model.Jenkins;
import net.minidev.json.JSONArray;
import okhttp3.*;

import java.io.IOException;
import java.util.*;

import static com.jayway.jsonpath.JsonPath.parse;
import static com.jayway.jsonpath.JsonPath.using;
import static com.jayway.jsonpath.Criteria.where;
import static com.jayway.jsonpath.Filter.filter;

/**
 * Created by Richard on 29/06/2017.
 */
@Extension
public class SwarmQueueListener extends QueueListener {

    @Override
    public void onEnterBuildable(Queue.BuildableItem bi) {
        Jenkins jenkins = Jenkins.getInstance();

        Computer computers[] = jenkins.getComputers();

        List<Computer> computersList = Arrays.asList(computers);

        Computer idleComputer = computersList.stream().filter(computer -> computer.countIdle() > 0).findFirst().orElse(null);
        if (idleComputer == null) {
            System.out.println("Would start new node");
        } else {
            System.out.println("Don't need to start new node");
            return;
        }

        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url("http://localhost:1234/services").build();
            Response response = client.newCall(request).execute();


            Filter myfilter = Filter.filter(where("Spec.Name").is("jenkins-agent_main"));
            ReadContext ctx = JsonPath.parse(response.body().string());
            JSONArray service = ctx.read("$[?]", myfilter);
            ReadContext serviceCtx = JsonPath.parse(service.get(0));
            String id = serviceCtx.read("$.ID");
            String version = serviceCtx.read("$.Version.Index", String.class);
            int replicas = serviceCtx.read("$.Spec.Mode.Replicated.Replicas");

            if (replicas < 5) {
                int requiredReplicas = replicas + 1;

                WriteContext writeContext = JsonPath.parse((HashMap) serviceCtx.read("$.Spec"));
                writeContext.set("$.Mode.Replicated.Replicas", requiredReplicas);

                String jsonString = writeContext.jsonString();
                request = new Request.Builder().url("http://localhost:1234/services/" + id + "/update?version=" + version).method("POST", RequestBody.create(MediaType.parse("application/json"), jsonString)).build();
                response = client.newCall(request).execute();
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    @Initializer
    public static void init() {
        Timer idleCheckTimer = new Timer(true);
        TimerTask task = new TimerTask() {

            @Override
            public void run() {
                Jenkins jenkins = Jenkins.getInstance();

                Computer computers[] = jenkins.getComputers();

                List<Computer> computersList = Arrays.asList(computers);
                long idleTime = System.currentTimeMillis() - 5000;
                OkHttpClient client = new OkHttpClient();

                computersList.stream().filter(computer -> computer.getIdleStartMilliseconds() <= idleTime).limit(computersList.size() - 1).forEach(computer -> {
                    try {
                        if (!computer.isIdle()) {
                            return;
                        }
                        computer.cliOffline("Offline");
                        String dockerContainerId = computer.getName().substring(0, computer.getName().indexOf("-"));
                        Request request = new Request.Builder().url("http://localhost:1234/containers/" + dockerContainerId + "?force=true").method("DELETE", RequestBody.create(MediaType.parse("application/json"), "{}")).build();
                        Response response = client.newCall(request).execute();
                    } catch (Exception e) {

                    }

                    try {
                        Request request = new Request.Builder().url("http://localhost:1234/services").build();
                        Response response = client.newCall(request).execute();

                        Filter myfilter = Filter.filter(where("Spec.Name").is("jenkins-agent_main"));
                        ReadContext ctx = JsonPath.parse(response.body().string());
                        JSONArray service = ctx.read("$[?]", myfilter);
                        ReadContext serviceCtx = JsonPath.parse(service.get(0));
                        String id = serviceCtx.read("$.ID");
                        String version = serviceCtx.read("$.Version.Index", String.class);
                        int replicas = serviceCtx.read("$.Spec.Mode.Replicated.Replicas");

                        if (replicas > 1) {
                            int requiredReplicas = replicas - 1;

                            WriteContext writeContext = JsonPath.parse((HashMap) serviceCtx.read("$.Spec"));
                            writeContext.set("$.Mode.Replicated.Replicas", requiredReplicas);

                            String jsonString = writeContext.jsonString();
                            request = new Request.Builder().url("http://localhost:1234/services/" + id + "/update?version=" + version).method("POST", RequestBody.create(MediaType.parse("application/json"), jsonString)).build();
                            response = client.newCall(request).execute();
                        }

                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }

                });

//                try {
//                    OkHttpClient client = new OkHttpClient();
//                    Request request = new Request.Builder().url("http://localhost:1234/services").build();
//                    Response response = client.newCall(request).execute();
//
//
            }
        };
        idleCheckTimer.scheduleAtFixedRate(task, 15 * 1000, 30 * 1000);
    }
}
