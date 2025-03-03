/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.transport.netty4;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.api.ChannelContextListener;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class NettyChannelHandler extends ChannelInboundHandlerAdapter {
    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(NettyChannelHandler.class);

    private final Map<String, Channel> dubboChannels;

    private final URL url;
    private final ChannelHandler handler;

    private final List<ChannelContextListener> contextListeners;

    public NettyChannelHandler(
            Map<String, Channel> dubboChannels,
            URL url,
            ChannelHandler handler,
            List<ChannelContextListener> listeners) {
        this.dubboChannels = dubboChannels;
        this.url = url;
        this.handler = handler;
        this.contextListeners = listeners;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        io.netty.channel.Channel ch = ctx.channel();
        NettyChannel channel = NettyChannel.getOrAddChannel(ch, url, handler);
        if (channel != null) {
            dubboChannels.put(NetUtils.toAddressString((InetSocketAddress) ch.remoteAddress()), channel);
            handler.connected(channel);
            contextListeners.forEach(listener -> {
                try {
                    listener.onConnect(ctx);
                } catch (Exception e) {
                    logger.warn("99-1", "", "", "", "Failed to invoke listener when channel connect:", e);
                }
            });
            if (logger.isInfoEnabled()) {
                logger.info(
                        "The connection {} of {} -> {} is established.",
                        ch,
                        channel.getRemoteAddressKey(),
                        channel.getLocalAddressKey());
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        io.netty.channel.Channel ch = ctx.channel();
        NettyChannel channel = NettyChannel.getOrAddChannel(ch, url, handler);
        try {
            dubboChannels.remove(NetUtils.toAddressString((InetSocketAddress) ch.remoteAddress()));
            if (channel != null) {
                handler.disconnected(channel);

                if (logger.isInfoEnabled()) {
                    logger.info(
                            "The connection {} of {} -> {} is disconnected.",
                            ch,
                            channel.getRemoteAddressKey(),
                            channel.getLocalAddressKey());
                }
            }
        } finally {
            NettyChannel.removeChannel(ch);
            contextListeners.forEach(listener -> {
                try {
                    listener.onDisconnect(ctx);
                } catch (Exception e) {
                    logger.warn("99-1", "", "", "", "Failed to invoke listener when channel disconnect:", e);
                }
            });
        }
    }
}
