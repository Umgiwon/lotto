package com.one;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.lang.reflect.Type;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 로또 프로그램
 *
 * 번호 생성 방식 :
 * 1회차(2002-12-07) ~ 현재 회차까지 반복
 * 1 ~ 45 까지의 숫자를 SecureRandom으로 6개 뽑는다.
 * 각 숫자들중 갯수가 많은 순서로 상위/하위 10개를 추출한다.
 * 추출된 숫자들로 조합해 상위 3세트, 하위 2세트를 만들어낸다.
 *
 * 동행복권 API를 통해서 1회차 ~ 현재 회차까지 데이터를 가져온 뒤
 * 빈도수 높은 상위 숫자 조합 3세트,
 * 빈도수 낮은 하위 숫자 조합 2세트를 만든다.
 */
@Slf4j
public class LottoNumberGenerator {
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CACHE_FILE = "lotto_frequency_cache.json";
    private static final Type FREQUENCY_TYPE = new TypeToken<Map<Integer, Map<Integer, Integer>>>(){}.getType();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int LOTTO_MAX_NUMBER = 45;
    private static final int NUMBERS_PER_SET = 6;
    private static final int TOP_NUMBERS_COUNT = 10;
    private static final LocalDate FIRST_DRAW_DATE = LocalDate.of(2002, 12, 7);
    private static final String LOTTO_API_URL = "https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo=%d";

    /**
     * 현재 로또 회차를 계산
     * 2002년 12월 7일 1회차 기준으로 주 단위 계산
     */
    private static int calculateCurrentRound() {
        long weeks = ChronoUnit.WEEKS.between(FIRST_DRAW_DATE, LocalDate.now());
        int currentRound = Math.toIntExact(weeks + 1);
        log.info("현재 로또 회차: {}", currentRound);
        return currentRound;
    }

    /**
     * SecureRandom을 사용한 시뮬레이션 데이터 생성
     * @param totalRounds 시뮬레이션할 총 회차 수
     * @return 번호별 빈도수 Map
     */
    private static Map<Integer, Integer> simulateRandomFrequency(int totalRounds) {
        Map<Integer, Integer> frequency = initializeFrequencyMap();

        log.info("SecureRandom 시뮬레이션 시작 (총 {}회차)", totalRounds);
        for (int round = 1; round <= totalRounds; round++) {
            Set<Integer> numbers = generateRandomNumberSet();
            numbers.forEach(num -> frequency.merge(num, 1, Integer::sum));

            if (round % 100 == 0) {
                log.info("시뮬레이션 진행률: {}/{}회차", round, totalRounds);
            }
        }

        return frequency;
    }

    /**
     * API를 통한 실제 당첨번호 빈도수 조회
     * @param currentRound 현재 회차
     * @return 번호별 빈도수 Map
     */
    private static Map<Integer, Integer> getActualLottoFrequency(int currentRound) throws IOException {
        Map<Integer, Map<Integer, Integer>> cachedData = loadCache();

        if (cachedData.containsKey(currentRound)) {
            log.info("캐시된 데이터 사용");
            return cachedData.get(currentRound);
        }

        Map<Integer, Integer> frequency = initializeFrequencyMap();
        log.info("API에서 데이터 수집 시작");

        for (int round = 1; round <= currentRound; round++) {
            processRoundData(round, frequency);
        }

        cachedData.put(currentRound, frequency);
        saveCache(cachedData);
        return frequency;
    }

    /**
     * 특정 회차의 당첨번호 처리
     */
    private static void processRoundData(int round, Map<Integer, Integer> frequency) {
        try {
            Thread.sleep(100); // API 부하 방지
            String url = String.format(LOTTO_API_URL, round);
            try (Response response = CLIENT.newCall(new Request.Builder().url(url).build()).execute()) {
                if (!response.isSuccessful()) return;

                JsonObject jsonObject = GSON.fromJson(response.body().string(), JsonObject.class);
                if (!"success".equals(jsonObject.get("returnValue").getAsString())) return;

                for (int i = 1; i <= NUMBERS_PER_SET; i++) {
                    int number = jsonObject.get("drwtNo" + i).getAsInt();
                    frequency.merge(number, 1, Integer::sum);
                }
            }

            if (round % 100 == 0) {
                log.info("API 데이터 수집 진행률: {}/{}회차", round, round);
            }
        } catch (Exception e) {
            log.error("{}회차 처리 중 오류 발생: {}", round, e.getMessage());
        }
    }

    /**
     * 주어진 숫자 풀에서 로또 번호 세트 생성
     */
    private static List<Integer> generateLottoNumbers(List<Integer> pool) {
        List<Integer> numbers = new ArrayList<>(pool);
        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < NUMBERS_PER_SET; i++) {
            int index = SECURE_RANDOM.nextInt(numbers.size());
            result.add(numbers.get(index));
            numbers.remove(index);
        }

        Collections.sort(result);
        return result;
    }

    /**
     * 빈도수 Map을 초기화
     */
    private static Map<Integer, Integer> initializeFrequencyMap() {
        return IntStream.rangeClosed(1, LOTTO_MAX_NUMBER)
                .boxed()
                .collect(Collectors.toMap(i -> i, i -> 0));
    }

    /**
     * 캐시된 데이터 로드
     */
    private static Map<Integer, Map<Integer, Integer>> loadCache() {
        File cacheFile = new File(CACHE_FILE);
        if (!cacheFile.exists()) return new HashMap<>();

        try (Reader reader = new FileReader(cacheFile)) {
            return GSON.fromJson(reader, FREQUENCY_TYPE);
        } catch (IOException e) {
            log.error("캐시 파일 로드 실패: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 캐시 데이터 저장
     */
    private static void saveCache(Map<Integer, Map<Integer, Integer>> data) {
        try (Writer writer = new FileWriter(CACHE_FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            log.error("캐시 파일 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 랜덤 번호 세트 생성
     */
    private static Set<Integer> generateRandomNumberSet() {
        Set<Integer> numbers = new HashSet<>();
        while (numbers.size() < NUMBERS_PER_SET) {
            numbers.add(SECURE_RANDOM.nextInt(LOTTO_MAX_NUMBER) + 1);
        }
        return numbers;
    }

    /**
     * 빈도수 기반 번호 풀 추출 (상위/하위)
     */
    private static List<Integer> extractNumberPool(Map<Integer, Integer> frequency, boolean useTopFrequency) {
        return frequency.entrySet().stream()
                .sorted(useTopFrequency ?
                        Map.Entry.<Integer, Integer>comparingByValue().reversed() :
                        Map.Entry.comparingByValue())
                .limit(TOP_NUMBERS_COUNT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 번호 세트 생성 및 출력
     */
    private static void generateAndPrintSets(List<Integer> numberPool, int setCount, String description) {
        System.out.println("\n[" + description + "]");
        IntStream.range(0, setCount)
                .mapToObj(i -> generateLottoNumbers(numberPool))
                .forEach(System.out::println);
    }

    public static void main(String[] args) {
        try {
            int currentRound = calculateCurrentRound();

            // 1. SecureRandom 시뮬레이션
            System.out.println("=== SecureRandom 시뮬레이션 기반 번호 세트 ===");
            Map<Integer, Integer> randomFrequency = simulateRandomFrequency(currentRound);

            List<Integer> randomTopNumbers = extractNumberPool(randomFrequency, true);
            List<Integer> randomBottomNumbers = extractNumberPool(randomFrequency, false);

            generateAndPrintSets(randomTopNumbers, 3, "시뮬레이션 고빈도 기반 세트");
            generateAndPrintSets(randomBottomNumbers, 2, "시뮬레이션 저빈도 기반 세트");

            // 2. 실제 당첨 데이터 기반
            System.out.println("\n=== 실제 당첨번호 분석 기반 번호 세트 ===");
            Map<Integer, Integer> apiFrequency = getActualLottoFrequency(currentRound);

            List<Integer> apiTopNumbers = extractNumberPool(apiFrequency, true);
            List<Integer> apiBottomNumbers = extractNumberPool(apiFrequency, false);

            generateAndPrintSets(apiTopNumbers, 3, "실제 당첨번호 고빈도 기반 세트");
            generateAndPrintSets(apiBottomNumbers, 2, "실제 당첨번호 저빈도 기반 세트");

        } catch (Exception e) {
            log.error("프로그램 실행 중 오류 발생: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}