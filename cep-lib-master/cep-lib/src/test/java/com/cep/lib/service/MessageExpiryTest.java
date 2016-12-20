package com.cep.lib.service;

import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import akka.routing.FromConfig;
import com.cep.lib.domain.ResponseMessage;
import com.cep.lib.service.MessageExpiryDetector.StateEventCountReq;
import com.cep.lib.service.MessageExpiryDetector.StateEventCountResp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static com.cep.lib.domain.ResponseMessage.ResponseType.MessageProcessed;
import static com.cep.lib.service.ResponseHandlerActor.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * These test cases are time sensitive and may fail if run with test suite because of GC pause etc. Run them as
 * part of separate suite or increase the expiry delay actor.state.message.expiry.millis to higher number and
 * adjust test cases delays accordingly.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = SpringConfig.class)
public class MessageExpiryTest {

    @Autowired
    private CepAkkaManager cepAkkaManager;

    @Autowired
    private SpringExtension springExtension;

    @Test
    public void shouldExpireMessagesAtProvidedInterval() throws Exception {

        // Given
        ActorRef messageExpiryDetector = ClusterSharding.get(cepAkkaManager.getCepActorSystem()).shardRegion("MessageExpiryShardRegion");

        // When
        ActorRef responseActor1 = springExtension.actorOf(cepAkkaManager.getCepActorSystem(), RESPONSE_HANDLER_ACTOR,
                new FromConfig(), RESPONSE_HANDLER_ACTOR_DISPATCHER);

        MessageExpiryEvent messageExpiryEvent = new MessageExpiryEvent(1, "1", 1);
        messageExpiryDetector.tell(messageExpiryEvent, responseActor1);
        ResponseMessage responseMessage1 = blockForResponse(responseActor1, 10, 5000);

        // Then
        assertThat(responseMessage1.getResponseType()).isEqualTo(MessageProcessed);

        // Message should not have expired.
        ActorRef responseActor2 = springExtension.actorOf(cepAkkaManager.getCepActorSystem(), RESPONSE_HANDLER_ACTOR,
                new FromConfig(), RESPONSE_HANDLER_ACTOR_DISPATCHER);
        messageExpiryDetector.tell(new StateEventCountReq(1, "1"), responseActor2);
        ResponseMessage responseMessage2 = blockForResponse(responseActor2, 10, 5000);
        assertThat(((StateEventCountResp) responseMessage2.getCepMessage()).messageExpiryEvents.size()).isEqualTo(1);
        assertThat(((StateEventCountResp) responseMessage2.getCepMessage()).messageExpiryEvents).extracting("eventId").contains(1);

        // Give sometime (more than application.yaml => actor.state.message.expiry.millis) so that message expires
        Thread.sleep(301);
        ActorRef responseActor3 = springExtension.actorOf(cepAkkaManager.getCepActorSystem(), RESPONSE_HANDLER_ACTOR,
                new FromConfig(), RESPONSE_HANDLER_ACTOR_DISPATCHER);
        messageExpiryDetector.tell(new StateEventCountReq(1, "1"), responseActor3);
        ResponseMessage responseMessage3 = blockForResponse(responseActor3, 10, 200);
        assertThat(((StateEventCountResp) responseMessage3.getCepMessage()).messageExpiryEvents.size()).isEqualTo(0);

    }


    @Test
    public void shouldExpireEachMessagesAtProvidedInterval() throws Exception {

        // Given
        ActorRef messageExpiryDetector = ClusterSharding.get(cepAkkaManager.getCepActorSystem()).shardRegion("MessageExpiryShardRegion");

        // When
        ActorRef responseActor1 = springExtension.actorOf(cepAkkaManager.getCepActorSystem(), RESPONSE_HANDLER_ACTOR,
                new FromConfig(), RESPONSE_HANDLER_ACTOR_DISPATCHER);
        messageExpiryDetector.tell(new MessageExpiryEvent(1, "2", 1), responseActor1);
        ResponseMessage responseMessage1 = blockForResponse(responseActor1, 10, 5000);
        assertThat(responseMessage1.getResponseType()).isEqualTo(MessageProcessed);

        // Keep a delay to send first message.
        Thread.sleep(100);
        ActorRef responseActor2 = springExtension.actorOf(cepAkkaManager.getCepActorSystem(), RESPONSE_HANDLER_ACTOR,
                new FromConfig(), RESPONSE_HANDLER_ACTOR_DISPATCHER);
        messageExpiryDetector.tell(new MessageExpiryEvent(1, "2", 2), responseActor2);
        ResponseMessage responseMessage2 = blockForResponse(responseActor2, 10, 5000);
        assertThat(responseMessage2.getResponseType()).isEqualTo(MessageProcessed);

        // Then => Message should not have expired.
        ActorRef responseActor3 = springExtension.actorOf(cepAkkaManager.getCepActorSystem(), RESPONSE_HANDLER_ACTOR,
                new FromConfig(), RESPONSE_HANDLER_ACTOR_DISPATCHER);
        messageExpiryDetector.tell(new StateEventCountReq(1, "2"), responseActor3);
        ResponseMessage responseMessage3 = blockForResponse(responseActor3, 10, 5000);
        assertThat(((StateEventCountResp) responseMessage3.getCepMessage()).messageExpiryEvents.size()).isEqualTo(2);
        assertThat(((StateEventCountResp) responseMessage3.getCepMessage()).messageExpiryEvents).extracting("eventId").contains(1, 2);

        // Give sometime (more than application.yaml => actor.state.message.expiry.millis) so that first message expires
        Thread.sleep(101);
        ActorRef responseActor4 = springExtension.actorOf(cepAkkaManager.getCepActorSystem(), RESPONSE_HANDLER_ACTOR,
                new FromConfig(), RESPONSE_HANDLER_ACTOR_DISPATCHER);
        messageExpiryDetector.tell(new StateEventCountReq(1, "2"), responseActor4);
        ResponseMessage responseMessage4 = blockForResponse(responseActor4, 10, 250);
        assertThat(((StateEventCountResp) responseMessage4.getCepMessage()).messageExpiryEvents.size()).isEqualTo(1);
        assertThat(((StateEventCountResp) responseMessage3.getCepMessage()).messageExpiryEvents).extracting("eventId").contains(2);

        // Give some more time (more than application.yaml => actor.state.message.expiry.millis) so that second message expires
        Thread.sleep(201);
        ActorRef responseActor5 = springExtension.actorOf(cepAkkaManager.getCepActorSystem(), RESPONSE_HANDLER_ACTOR,
                new FromConfig(), RESPONSE_HANDLER_ACTOR_DISPATCHER);
        messageExpiryDetector.tell(new StateEventCountReq(1, "2"), responseActor5);
        ResponseMessage responseMessage5 = blockForResponse(responseActor5, 10, 250);
        assertThat(((StateEventCountResp) responseMessage5.getCepMessage()).messageExpiryEvents.size()).isEqualTo(0);

    }

}
