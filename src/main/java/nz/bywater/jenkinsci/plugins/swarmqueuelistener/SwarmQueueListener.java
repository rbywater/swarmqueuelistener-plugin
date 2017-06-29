package nz.bywater.jenkinsci.plugins.swarmqueuelistener;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import jenkins.model.Jenkins;
import hudson.model.Node;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Richard on 29/06/2017.
 */
@Extension
public class SwarmQueueListener extends QueueListener {

    @Override
    public void onEnterBuildable(Queue.BuildableItem bi) {
        Jenkins jenkins = Jenkins.getInstance();

        List<Node> nodes = jenkins.getNodes();

        nodes.forEach((node) -> System.out.println(node.isAcceptingTasks()));

        if (bi.isBlocked()) {
            try {
                OkHttpClient client = new OkHttpClient();

                Request request = new Request.Builder().url("http://www.google.co.nz").build();
                Response response = client.newCall(request).execute();

                System.out.println(response.body().string());
            } catch (IOException ie) {
                ie.printStackTrace();
            }
        }
    }
}
