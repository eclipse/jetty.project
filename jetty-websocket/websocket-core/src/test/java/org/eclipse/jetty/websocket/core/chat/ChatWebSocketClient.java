//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.core.chat;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.BatchMode;
import org.eclipse.jetty.websocket.core.TextMessageHandler;
import org.eclipse.jetty.websocket.core.client.UpgradeRequest;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatWebSocketClient
{
    private static Logger LOG = Log.getLogger(ChatWebSocketClient.class);

    private URI baseWebsocketUri;
    private WebSocketCoreClient client;
    private TextMessageHandler handler;
    private String name = String.format("unknown@%x", ThreadLocalRandom.current().nextInt());

    public ChatWebSocketClient(String hostname, int port) throws Exception
    {
        this.baseWebsocketUri = new URI("ws://" + hostname + ":" + port);
        this.client = new WebSocketCoreClient();
        this.client.start();

        URI wsUri = baseWebsocketUri.resolve("/chat");
        handler = TextMessageHandler.from(this::onText);
        UpgradeRequest request = UpgradeRequest.from(client, wsUri, handler);
        request.setSubProtocols("chat");
        client.connect(request).get(5, TimeUnit.SECONDS);
    }

    public void onText(String message)
    {
        System.out.println(message);
    }

    private static final Pattern COMMAND_PATTERN = Pattern.compile("/([^\\s]+)(\\s+([^\\s]+))?", Pattern.CASE_INSENSITIVE);

    private void chat(String line)
    {
        if(line.startsWith("/"))
        {
            Matcher matcher = COMMAND_PATTERN.matcher(line);
            if (matcher.matches())
            {
                String command = matcher.group(1);
                String value = (matcher.groupCount()>1) ? matcher.group(2) : null;

                switch(command)
                {
                    case "name":
                        if (value != null && value.length() > 0)
                        {
                            name = value;
                            LOG.debug("name changed: " + name);
                        }
                        break;

                    case "exit":
                        handler.getCoreSession().close(Callback.from(()->System.exit(0),x->{x.printStackTrace();System.exit(1);}));
                        break;
                }

                return;
            }
        }
        LOG.debug("sending {}...",line);
        
        handler.sendText(Callback.from(()->LOG.debug("message sent"), LOG::warn),BatchMode.AUTO,name,": ",line);
    }

    public static void main(String[] args)
    {
        String hostname = "localhost";
        int port = 8888;

        if (args.length > 0)
            hostname = args[0];

        if (args.length > 1)
            port = Integer.parseInt(args[1]);

        ChatWebSocketClient client = null;
        try
        {
            client = new ChatWebSocketClient(hostname, port);

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));

            System.err.println("Type to chat, or:\n  /name <name> - to set member name\n  /exit - to exit\n");
            String line = in.readLine();
            while(line!=null)
            {
                line = line.trim();
                if (line.length()>0)
                    client.chat(line);
                line = in.readLine();
            }

        }
        catch (Throwable t)
        {
            t.printStackTrace(System.err);
        }
    }
}
