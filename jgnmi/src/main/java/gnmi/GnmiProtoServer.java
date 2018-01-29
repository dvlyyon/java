package gnmi;

import java.util.logging.Level;
import java.util.logging.Logger;

import gnmi.Gnmi.CapabilityRequest;
import gnmi.Gnmi.CapabilityResponse;
import gnmi.Gnmi.Encoding;
import gnmi.Gnmi.ModelData;
import gnmi.Gnmi.SubscribeRequest;
import gnmi.Gnmi.SubscribeResponse;
import gnmi.Gnmi.Subscription;
import gnmi.Gnmi.SubscriptionList;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class GnmiProtoServer extends gNMIGrpc.gNMIImplBase {
	private static final Logger logger = Logger.getLogger(GnmiProtoServer.class.getName());

	public void capabilities(CapabilityRequest request,
			io.grpc.stub.StreamObserver<CapabilityResponse> responseObserver) {
		Encoding coding = Encoding.PROTO;
		ModelData model = ModelData.newBuilder()
				.setName("ne")
				.setOrganization("com.coriant")
				.setVersion("0.6.0")
				.build();

		CapabilityResponse reply = CapabilityResponse
				.newBuilder()
				.setGNMIVersion("0.1.0")
				.addSupportedEncodings(coding)
				.addSupportedModels(model)
				.build();
		responseObserver.onNext(reply);
		responseObserver.onCompleted();	
	}


	public io.grpc.stub.StreamObserver<SubscribeRequest> subscribe(
			io.grpc.stub.StreamObserver<SubscribeResponse> responseObserver) {
		return new StreamObserver<SubscribeRequest>() {
			boolean initialized = false;
			@Override
			public void onNext(SubscribeRequest request) {
				SubscriptionList slist = request.getSubscribe();

				if (slist == null && !initialized) {
					responseObserver.onError(Status.INVALID_ARGUMENT
							.withDescription(String.format("Method %s is unimplemented",
									"gnmi.subscribe"))
							.asRuntimeException());
				}
				int subNum = slist.getSubscriptionCount();
				for (int i=0; i<subNum; i++) {
					Subscription sub = slist.getSubscription(0);
				}
				// Respond with all previous notes at this location.
				for (SubscribeResponse resp : FakeData.getAllCurrentData()) {
					responseObserver.onNext(resp);
				}
				responseObserver.onNext(FakeData.getSyncComplete());
			}

			@Override
			public void onError(Throwable t) {
				logger.log(Level.WARNING, "subscribe cancelled");
			}

			@Override
			public void onCompleted() {
				responseObserver.onCompleted();
			}
		};
	}

}