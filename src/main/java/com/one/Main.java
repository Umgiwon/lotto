package com.one;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 로또 프로그램
 *
 * 번호 생성 방식 :
 * 1회차(2002-12-07) ~ 현재 회차까지 반복
 * 1 ~ 45 까지의 숫자를 SecureRandom으로 6개 뽑는다.
 * 각 숫자들중 갯수가 많은 순서로 상위/하위 10개를 추출한다.
 * 추출된 숫자들로 조합해 상위 5세트, 하위 5세트를 만들어낸다.
 */
public class Main {
    public static void main(String[] args) {
        // 로또 1회차 시작일 설정 (토요일)
        LocalDate startDate = LocalDate.of(2002, 12, 7);
        // 현재 날짜 가져오기
        LocalDate currentDate = LocalDate.now();
        
        // 현재 날짜가 금요일이라면 아직 이번주 추첨이 안된 것이므로 저번주 토요일까지만 계산
        if (currentDate.getDayOfWeek() == DayOfWeek.FRIDAY) {
            currentDate = currentDate.minusWeeks(1);
        }
        // 현재 날짜가 토요일이 아니라면, 지난 토요일로 날짜 조정
        while (currentDate.getDayOfWeek() != DayOfWeek.SATURDAY) {
            currentDate = currentDate.minusDays(1);
        }
        
        // 현재 회차 계산 (시작일부터 현재까지의 주차 + 1)
        long weeksBetween = ChronoUnit.WEEKS.between(startDate, currentDate);
        int currentRound = (int) weeksBetween + 1;
        
        System.out.printf("마지막 추첨일: %s (%d회)\n", currentDate, currentRound);
        
        // SecureRandom 인스턴스 생성
        SecureRandom secureRandom = new SecureRandom();
        
        // 1~45까지의 숫자 출현 빈도를 저장할 Map
        Map<Integer, Integer> numberFrequency = new HashMap<>();
        // 1~45까지의 모든 숫자를 0으로 초기화
        for (int i = 1; i <= 45; i++) {
            numberFrequency.put(i, 0);
        }
        
        // 1 ~ 현재 회차까지 반복
        for (int i = 1; i <= currentRound; i++) {
            // 중복을 피하기 위해 Set 사용
            Set<Integer> lotto = new HashSet<>();
            
            // 6개의 숫자가 추출될 때까지 반복
            while (lotto.size() < 6) {
                // 1 ~ 45 사이의 난수 생성 (SecureRandom 사용)
                int number = secureRandom.nextInt(45) + 1;
                lotto.add(number);
            }
            
            // 추출된 번호들의 빈도수 증가
            for (int number : lotto) {
                numberFrequency.put(number, numberFrequency.get(number) + 1);
            }
        }
        
        // 빈도수를 기준으로 내림차순 정렬
        List<Map.Entry<Integer, Integer>> sortedNumbers = new ArrayList<>(numberFrequency.entrySet());
        sortedNumbers.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        
        // 상위 10개 숫자 추출
        System.out.println("\n=== 가장 많이 나온 상위 10개 숫자 ===");
        List<Integer> top10Numbers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map.Entry<Integer, Integer> entry = sortedNumbers.get(i);
            System.out.printf("%d번: %d회 출현\n", entry.getKey(), entry.getValue());
            top10Numbers.add(entry.getKey());
        }
        
        // 하위 10개 숫자 추출
        System.out.println("\n=== 가장 적게 나온 하위 10개 숫자 ===");
        List<Integer> bottom10Numbers = new ArrayList<>();
        for (int i = sortedNumbers.size() - 10; i < sortedNumbers.size(); i++) {
            Map.Entry<Integer, Integer> entry = sortedNumbers.get(i);
            System.out.printf("%d번: %d회 출현\n", entry.getKey(), entry.getValue());
            bottom10Numbers.add(entry.getKey());
        }
        
        // 다음 회차 정보 출력 (다음 토요일)
        LocalDate nextDrawDate = currentDate.plusWeeks(1);
        System.out.printf("\n다음 추첨일: %s (%d회)\n", nextDrawDate, currentRound + 1);
        
        // 상위 10개 숫자로 5세트 생성
        System.out.println("\n=== 상위 10개 숫자로 만든 5세트 추천 번호 ===");
        for (int set = 1; set <= 5; set++) {
            Collections.shuffle(top10Numbers);
            List<Integer> selectedNumbers = new ArrayList<>(top10Numbers.subList(0, 6));
            Collections.sort(selectedNumbers);
            
            System.out.printf("세트 %d: [ ", set);
            for (int num : selectedNumbers) {
                System.out.printf("%02d ", num);
            }
            System.out.println("]");
        }
        
        // 하위 10개 숫자로 5세트 생성
        System.out.println("\n=== 하위 10개 숫자로 만든 5세트 추천 번호 ===");
        for (int set = 1; set <= 5; set++) {
            Collections.shuffle(bottom10Numbers);
            List<Integer> selectedNumbers = new ArrayList<>(bottom10Numbers.subList(0, 6));
            Collections.sort(selectedNumbers);
            
            System.out.printf("세트 %d: [ ", set);
            for (int num : selectedNumbers) {
                System.out.printf("%02d ", num);
            }
            System.out.println("]");
        }
    }
}