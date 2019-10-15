/*
 * Copyright 2016, gRPC Authors All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dvlyyon.nbi.gnmi;

import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

import io.grpc.Attributes;
import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.Status;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCalls.UnaryMethod;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSession;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.dvlyyon.nbi.gnmi.GnmiDialOutProtoServer.SubscribeStreamObserver;

import gnmi.Gnmi.CapabilityRequest;
import gnmi.Gnmi.CapabilityResponse;
import gnmi.Gnmi.Encoding;
import gnmi.Gnmi.ModelData;
import gnmi.Gnmi.Notification;
import gnmi.Gnmi.Path;
import gnmi.Gnmi.PathElem;
import gnmi.Gnmi.SubscribeRequest;
import gnmi.Gnmi.SubscribeResponse;
import gnmi.Gnmi.Subscription;
import gnmi.Gnmi.SubscriptionList;
import gnmi.Gnmi.TypedValue;
import gnmi.Gnmi.Update;
import gnmi.gNMIGrpc;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 *
 * <p>This is an advanced example of how to swap out the serialization logic.  Normal users do not
 * need to do this.  This code is not intended to be a production-ready implementation, since JSON
 * encoding is slow.  Additionally, JSON serialization as implemented may be not resilient to
 * malicious input.
 *
 * <p>If you are considering implementing your own serialization logic, contact the grpc team at
 * https://groups.google.com/forum/#!forum/grpc-io
 */
public class GnmiServer 
implements GnmiTransportListenerInf, GnmiRPCListenerInf, GnmiNBIMgrInf {
	private static final Logger logger = Logger.getLogger(GnmiServer.class.getName());

	private Server server;


	private Map <String,GnmiSessionMgr> sessions = new HashMap<String,GnmiSessionMgr>();
	private Map <String,GnmiSessionMgr> currentThreads = new HashMap<String,GnmiSessionMgr>();
	private final static String ANONYMOUS_NAME = "AnonymousName";
	
	@Override
	public void addSession(String remoteClient) {
		synchronized (sessions) {
			GnmiSessionMgr s = sessions.get(remoteClient);
			if (s != null) {
				logger.severe("Duplicated session ID: "+remoteClient);
			}
			s = new GnmiSessionMgr(remoteClient);
			sessions.put(remoteClient, s);	
		}
	}

	@Override
	public void deleteSession(String remoteClient) {
		synchronized(sessions) {
			GnmiSessionMgr s = sessions.remove(remoteClient);
			if (s == null) {
				logger.severe("NOT exist session ID: "+remoteClient);
			} else {
				s.close();
			}
		}
	}

	@Override
	public void prepareAcceptRPC(String threadName, String sessionID) {
		GnmiSessionMgr s = null;
		synchronized (sessions) {
			s = sessions.get(sessionID);
		}
		if (s == null) {
			logger.severe("NOT exist session ID:" + sessionID);
		} else {
			s.prepareAcceptRPC(threadName);
		}
		synchronized (currentThreads) {
			s = currentThreads.get(threadName);
			if (s != null) {
				logger.severe("NOT expected status for session: " + sessionID);
			}
			currentThreads.put(threadName, s);
		}
	}

	@Override
	public void registerRPC(String threadName, GnmiServerStreamObserver observer) {
		synchronized (currentThreads) {
			GnmiSessionMgr s = currentThreads.get(threadName);
			if (s == null) {
				logger.severe("NOT excepted status for regiester RPC");
			} else {
				s = new GnmiSessionMgr(ANONYMOUS_NAME);
			}
			s.registerRPC(observer);
			currentThreads.remove(threadName);
		}
		
	}

	@Override
	public Object pop() {
		Object [] sessionList = null;
		synchronized(sessions) {
			Collection c = sessions.values();
			sessionList = c.toArray();
		}
		if (sessionList != null) {
			for (Object session:sessionList) {
				GnmiSessionMgr sm = (GnmiSessionMgr)session;
				Object obj = sm.pop();
				if (obj != null) return obj;
			}
		}
		return null;
	}

	@Override
	public Object pop(String sessionId) {
		synchronized(sessions) {
			GnmiSessionMgr mgr = sessions.get(sessionId);
			if (mgr == null)
				throw new RuntimeException("NOT identify session:" + sessionId);
			return mgr.pop();
		}
	}

	@Override
	public boolean isClosed(String sessionId) {
		synchronized(sessions) {
			GnmiSessionMgr mgr = sessions.get(sessionId);
			if (mgr == null)
				throw new RuntimeException("NOT identify session:" + sessionId);
			return mgr.isClosed();
		}
	}

	@Override
	public void shutdown(String sessionId) {
		synchronized(sessions) {
			GnmiSessionMgr mgr = sessions.get(sessionId);
			if (mgr == null)
				throw new RuntimeException("NOT identify session:" + sessionId);
			mgr.shutdown();
		}
	}

	@Override
	public Object pop(String sessionId, String streamId) {
		synchronized(sessions) {
			GnmiSessionMgr mgr = sessions.get(sessionId);
			if (mgr == null)
				throw new RuntimeException("NOT identify session:" + sessionId);
			return mgr.pop(streamId);
		}
	}
	
	@Override
	public Set<String> getSessions() {
		Set<String> lst = null;
		synchronized (sessions) {
			lst = sessions.keySet();
		}
		return lst;
	}

	@Override
	public Set<String> getRPCs(String sessionId) {
		synchronized(sessions) {
			GnmiSessionMgr mgr = sessions.get(sessionId);
			if (mgr == null) throw new RuntimeException("Not exist for session:" + sessionId);
			return mgr.getRPCs();
		}
	}

	private void start(GnmiServerContextInf cmd) throws Exception {
		AuthInterceptor interceptor = new AuthInterceptor(cmd, this);
		BindableService gNMIImpl = GnmiServerHelper.getGnmiServer(cmd,this);
		ServerTransportFilter filter = new GnmiTransportFilter(this);
        server = GnmiServerHelper.startServer(cmd, gNMIImpl, interceptor,filter);
        server.start();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.err.println("*** shutting down gRPC server since JVM is shutting down");
				GnmiServer.this.stop();
				System.err.println("*** server shut down");
			}
		});
	}

	private void stop() {
		if (server != null) {
			server.shutdown();
		}
	}

	/**
	 * Await termination on the main thread since the grpc library uses daemon threads.
	 */
	private void blockUntilShutdown() throws InterruptedException {
		if (server != null) {
			server.awaitTermination();
		}
	}

	/**
	 * Main launches the server from the command line.
	 */
	public static void main(String[] args) throws Exception {
		final GnmiServer server = new GnmiServer();
		try {
			server.start(new GnmiServerCmdContext(args));
			new Thread(new UpdateExecutor(server)).start();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		server.blockUntilShutdown();
	}


	static class UpdateExecutor implements Runnable {
		GnmiServer server = null;

		public UpdateExecutor(GnmiServer server) {
			this.server = server;
		}
		
		private void printSetString(Set<String> s, String title) {
			if (s == null || s.size() == 0) return;
			System.out.println(title + ":");
			s.forEach(v->{
				System.out.println("    "+v);
			});
		}
		
		@Override
		public void run() {
			while(true) {
				Object update = server.pop();
				printSetString(server.getSessions(),"Session");
				if (update == null) {
					try {
						Thread.currentThread().sleep(10 * 1000);
					} catch (Exception e) {
						e.printStackTrace();
					}					
				} else {
					System.out.println(update);
				}
			}			
		}
	}


}
