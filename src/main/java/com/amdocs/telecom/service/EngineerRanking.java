package com.amdocs.telecom.service;

import com.amdocs.telecom.dto.EngineerRecommendation;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class EngineerRanking {
    private EngineerRanking() { }
    // UTF-8 byte order matches the DAO's PostgreSQL COLLATE "C", including non-ASCII codes.
    private static int codes(String left,String right) {
        byte[] a=left.getBytes(StandardCharsets.UTF_8), b=right.getBytes(StandardCharsets.UTF_8);
        for(int i=0;i<Math.min(a.length,b.length);i++) {
            int compared=Integer.compare(a[i]&255,b[i]&255);
            if(compared!=0) { return compared; }
        }
        return Integer.compare(a.length,b.length);
    }
    public static List<EngineerRecommendation> topThree(List<EngineerRecommendation> candidates) {
        Comparator<EngineerRecommendation> order=Comparator.comparingLong(EngineerRecommendation::getActiveWorkload)
                .thenComparing(Comparator.comparingInt((EngineerRecommendation value) -> value.getEngineer().getExperienceYears()).reversed())
                .thenComparing(value -> value.getEngineer().getEmployeeCode(),EngineerRanking::codes);
        return Collections.unmodifiableList(candidates.stream().sorted(order).limit(3).collect(Collectors.toList()));
    }
}
