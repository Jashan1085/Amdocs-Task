package com.amdocs.telecom.service;

import com.amdocs.telecom.dto.EngineerRecommendation;
import com.amdocs.telecom.model.NetworkEngineer;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class EngineerRankingTest {
    private EngineerRecommendation engineer(String code,int experience,long workload) {
        return new EngineerRecommendation(new NetworkEngineer(1,code,"Engineer","IP Network","West",experience,true,Instant.EPOCH,Instant.EPOCH),workload);
    }
    @Test void ranksWorkloadBeforeExperienceThenEmployeeCodeAndLimitsToThree() {
        EngineerRecommendation busy=engineer("BUSY",50,1),junior=engineer("JUNIOR",1,0),
                second=engineer("B",5,0),first=engineer("A",5,0);
        assertThat(EngineerRanking.topThree(Arrays.asList(busy,junior,second,first))).containsExactly(first,second,junior);
    }
    @Test void emptyMatchesRemainEmpty() { assertThat(EngineerRanking.topThree(Collections.emptyList())).isEmpty(); }
    @Test void codeTieBreakMatchesDatabaseUtf8ByteOrder() {
        EngineerRecommendation first=engineer("A",5,0),second=engineer("é",5,0),third=engineer("\uE000",5,0),fourth=engineer("\uD800\uDC00",5,0);
        assertThat(EngineerRanking.topThree(Arrays.asList(fourth,third,second,first))).containsExactly(first,second,third);
    }
}
