package com.one;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * 로또 API 연계하여 캐싱 파일 생성하는 class
 */
public class LottoFrequencyAnalyzer {
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String CACHE_FILE = "lotto_frequency_cache.json";
    private static final Type FREQUENCY_TYPE = new TypeToken<Map<Integer, Map<Integer, Integer>>>(){}.getType();

    public static Map<Integer, Integer> getActualLottoFrequency(int currentRound) throws IOException {
        // 캐시 파일 확인
        Map<Integer, Map<Integer, Integer>> cachedData = loadCache();
        Map<Integer, Integer> frequency;
        
        if (cachedData.containsKey(currentRound)) {
            System.out.println("캐시된 데이터를 사용합니다.");
            return cachedData.get(currentRound);
        }

        System.out.println("API에서 새로운 데이터를 가져옵니다...");
        frequency = new HashMap<>();
        for (int i = 1; i <= 45; i++) {
            frequency.put(i, 0);
        }

        for (int round = 1; round <= currentRound; round++) {
            String url = String.format("https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo=%d", round);
            Request request = new Request.Builder().url(url).build();

            try {
                Thread.sleep(100);
                Response response = client.newCall(request).execute();
                
                if (!response.isSuccessful()) {
                    System.out.printf("%d회차 데이터 가져오기 실패: %s\n", round, response.code());
                    response.close();
                    continue;
                }

                String responseBody = response.body().string();
                JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

                // returnValue 체크 추가
                if (jsonObject.has("returnValue") && !jsonObject.get("returnValue").getAsString().equals("success")) {
                    System.out.printf("%d회차 데이터 없음\n", round);
                    response.close();
                    continue;
                }

                try {
                    for (int i = 1; i <= 6; i++) {
                        String key = "drwtNo" + i;
                        if (jsonObject.has(key)) {
                            int number = jsonObject.get(key).getAsInt();
                            frequency.put(number, frequency.get(number) + 1);
                        }
                    }
                } catch (Exception e) {
                    System.out.printf("%d회차 데이터 처리 중 오류: %s\n", round, e.getMessage());
                }

                response.close();
                
                if (round % 10 == 0) {
                    System.out.printf("진행 중: %d/%d 회차 처리 완료\n", round, currentRound);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("API 요청 중 인터럽트가 발생했습니다.", e);
            } catch (Exception e) {
                System.out.printf("%d회차 처리 중 예외 발생: %s\n", round, e.getMessage());
            }
        }

        // 캐시에 저장
        cachedData.put(currentRound, frequency);
        saveCache(cachedData);

        return frequency;
    }

    private static Map<Integer, Map<Integer, Integer>> loadCache() {
        File cacheFile = new File(CACHE_FILE);
        if (!cacheFile.exists()) {
            return new HashMap<>();
        }

        try (Reader reader = new FileReader(cacheFile)) {
            return gson.fromJson(reader, FREQUENCY_TYPE);
        } catch (IOException e) {
            System.out.println("캐시 파일 읽기 실패. 새로운 캐시를 생성합니다.");
            return new HashMap<>();
        }
    }

    private static void saveCache(Map<Integer, Map<Integer, Integer>> data) {
        try (Writer writer = new FileWriter(CACHE_FILE)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            System.out.println("캐시 파일 저장 실패: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            int currentRound = 1110; // 현재 회차
            Map<Integer, Integer> frequency = getActualLottoFrequency(currentRound);
            System.out.println("\n번호별 출현 빈도:");
            frequency.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue((a, b) -> b - a))
                    .forEach(entry -> System.out.printf("%d번: %d회\n", entry.getKey(), entry.getValue()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}