package com.amdocs.telecom.service;
import com.amdocs.telecom.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;
class SlaEvaluationTest {
    private final Instant start=Instant.parse("2026-10-15T12:00:00Z");
    private TroubleTicket ticket(TicketStatus status,Instant response,Instant resolved) {
        return new TroubleTicket(1,"TT-test",1,1L,IncidentCategory.OTHER,"Test",TicketPriority.HIGH,Severity.HIGH,1,status,
                start.plusSeconds(600),start.plusSeconds(6000),response,resolved,status==TicketStatus.CLOSED ? resolved : null,0,start,start);
    }
    private SlaEvaluation.Assessment evaluate(TicketStatus status,Instant response,Instant resolved,long age) {
        return SlaEvaluation.assess(ticket(status,response,resolved),new SLAConfiguration(TicketPriority.HIGH,1,10,100,80,start),start.plusSeconds(age));
    }
    @ParameterizedTest @CsvSource({"79,ON_TRACK","80,AT_RISK","99,AT_RISK","100,BREACHED","101,BREACHED"})
    void warningAndDeadlineAreInclusive(long age,SlaEvaluation.State expected) {
        assertThat(SlaEvaluation.metric(start,start.plusSeconds(100),null,start.plusSeconds(age),80,false).getState()).isEqualTo(expected);
    }
    @Test void completedAtDeadlineMeetsItButOneNanosecondLaterBreachesWithoutRemainingActive() {
        assertThat(SlaEvaluation.metric(start,start.plusSeconds(100),start.plusSeconds(100),start.plusSeconds(200),80,false).getState()).isEqualTo(SlaEvaluation.State.MET);
        SlaEvaluation.Metric late=SlaEvaluation.metric(start,start.plusSeconds(100),start.plusSeconds(100).plusNanos(1),start.plusSeconds(200),80,false);
        assertThat(late.getState()).isEqualTo(SlaEvaluation.State.BREACHED); assertThat(late.isCompleted()).isTrue();
        assertThat(late.active(SlaEvaluation.State.BREACHED)).isFalse();
    }
    @ParameterizedTest @CsvSource({"479,0","480,1","599,1","600,2","11999,2","12000,3"})
    void escalationLevelsHaveExactWarningBreachAndExtraResolutionDurationBoundaries(long age,int expected) {
        assertThat(evaluate(TicketStatus.OPEN,null,null,age).getRequiredLevel()).isEqualTo(expected);
    }
    @Test void responseAndResolutionAreIndependentIncludingPendingCustomer() {
        SlaEvaluation.Assessment pending=evaluate(TicketStatus.PENDING_CUSTOMER,start.plusSeconds(10),null,4800);
        assertThat(pending.getResponse().getState()).isEqualTo(SlaEvaluation.State.MET);
        assertThat(pending.getResolution().getState()).isEqualTo(SlaEvaluation.State.AT_RISK);
        assertThat(pending.getRequiredLevel()).isEqualTo(1);
        assertThat(evaluate(TicketStatus.IN_PROGRESS,start.plusSeconds(601),null,700).getRequiredLevel()).isZero();
    }
    @Test void cancelledWorkIsNotApplicableAndResolvedLateWorkDoesNotEscalate() {
        assertThat(evaluate(TicketStatus.CANCELLED,null,null,15000).getResponse().getState()).isEqualTo(SlaEvaluation.State.NOT_APPLICABLE);
        assertThat(evaluate(TicketStatus.CANCELLED,null,null,15000).getRequiredLevel()).isZero();
        SlaEvaluation.Assessment done=evaluate(TicketStatus.RESOLVED,start.plusSeconds(1),start.plusSeconds(6001),15000);
        assertThat(done.getResolution().getState()).isEqualTo(SlaEvaluation.State.BREACHED); assertThat(done.getRequiredLevel()).isZero();
    }
    @Test void remainingTimeUsesTheCurrentClockUntilCompletionAndThenStops() {
        assertThat(SlaEvaluation.metric(start,start.plusSeconds(100),null,start.plusSeconds(20),80,false).getRemainingSeconds()).isEqualTo(80);
        assertThat(SlaEvaluation.metric(start,start.plusSeconds(100),null,start.plusSeconds(101),80,false).getRemainingSeconds()).isEqualTo(-1);
        assertThat(SlaEvaluation.metric(start,start.plusSeconds(100),start.plusSeconds(99),start.plusSeconds(200),80,false).getRemainingSeconds()).isEqualTo(1);
    }
}
