package org.dvlyyon.nbi.gnmi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.dvlyyon.nbi.gnmi.GnmiDialOutProtoServer.SubscribeStreamObserver;

import io.grpc.stub.StreamObserver;

public class GnmiSessionMgr
implements GnmiSessionSBIMgrInf,GnmiSessionInternalNBIMgrInf {
	private static final Logger logger = Logger.getLogger(GnmiSessionMgr.class.getName());
	String prepare = null;
	String name = null;
	boolean isClosed = false;
	Map <String, GnmiServerStreamObserver> rpcMap = null;
	
	public GnmiSessionMgr(String name) {
		this.name = name;
		this.rpcMap = new HashMap<String,GnmiServerStreamObserver>();
	}

	public void close() {
		isClosed = true;		
	}

	public void prepareAcceptRPC(String threadName) {
		prepare = threadName;
	}

	public void registerRPC(GnmiServerStreamObserver observer) {
		synchronized(rpcMap) {
			rpcMap.put(observer.getID(),(GnmiServerStreamObserver)observer);
		}
	}

	@Override
	public Object pop() {
		Object [] streams = null;
		synchronized(rpcMap) {
			Collection c = rpcMap.values();
			streams = c.toArray();
		}
		if (streams != null) {
			for (Object stream:streams) {
				GnmiServerStreamObserver q = (GnmiServerStreamObserver)stream;
				Object obj = q.poll();
				if (obj != null) return obj;
			}
		}
		return null;
	}

	@Override
	public boolean isClosed() {
		return isClosed;
	}

	@Override
	public void shutdown() {
		rpcMap.clear();
	}

	@Override
	public Object pop(String streamName) {
		synchronized(rpcMap) {
			GnmiServerStreamObserver stream = rpcMap.get(streamName);
			if (stream != null)
				return stream.poll();
			else {
				throw new RuntimeException("No such rpc:" + streamName);
			}
		}
	}
	
	@Override
	public Set<String> getRPCs() {
		return rpcMap.keySet();
	}
}
