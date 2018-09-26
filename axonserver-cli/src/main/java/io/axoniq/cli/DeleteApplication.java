package io.axoniq.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;

/**
 * Author: marc
 */
public class DeleteApplication extends AxonIQCliCommand {

    public static void run(String[] args) throws IOException {
        CommandLine commandLine = processCommandLine(args[0], args, CommandOptions.APPLICATION, CommandOptions.TOKEN);
        String url = createUrl(commandLine, "/v1/applications", CommandOptions.APPLICATION);

        // get http client
        try (CloseableHttpClient httpclient = createClient(commandLine)) {
            delete(httpclient, url, 200, commandLine.getOptionValue(CommandOptions.TOKEN.getOpt()));
        }
    }
}