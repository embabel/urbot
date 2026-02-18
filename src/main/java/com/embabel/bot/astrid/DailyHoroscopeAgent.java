package com.embabel.bot.astrid;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.library.HasContent;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.ModelSelectionCriteria;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Agent(description = "Generate a personalized daily horoscope by calculating the user's natal chart, current planetary transits, and interpreting the transit-to-natal aspects")
@Profile("astrid")
public record DailyHoroscopeAgent(AstrologyTools astrologyTools, GeocodingService geocodingService) {

    @JsonClassDescription("Request for a daily horoscope reading based on birth details")
    public record HoroscopeRequest(
            @JsonPropertyDescription("Birth date/time in ISO 8601 format, e.g. '1990-07-04T15:30:00+01:00'")
            String birthDateTimeIso,
            @JsonPropertyDescription("Birth place — city and country, e.g. 'Lagos, Nigeria'. Must be a real known location, never 'unknown'. Ask the user if not known.")
            String birthPlace
    ) {
    }

    public record GeocodedLocation(double latitude, double longitude) {
    }

    public record NatalChart(String chartData) {
    }

    public record CurrentTransits(String transitData) {
    }

    @JsonClassDescription("A personalized daily horoscope reading")
    public record HoroscopeReading(String reading) implements HasContent {
        @Override
        @NonNull
        public String getContent() {
            return reading;
        }
    }

    @Action
    GeocodedLocation geocode(HoroscopeRequest request) {
        var place = request.birthPlace();
        if (place == null || place.isBlank() || place.equalsIgnoreCase("unknown")) {
            throw new IllegalArgumentException(
                    "Birth place is required but was '%s'. Ask the user where they were born.".formatted(place));
        }
        var coords = geocodingService.geocode(place);
        if (coords == null) {
            throw new IllegalArgumentException("Could not geocode birth place: " + place);
        }
        return new GeocodedLocation(coords.latitude(), coords.longitude());
    }

    @Action
    NatalChart calculateNatalChart(HoroscopeRequest request, GeocodedLocation location) {
        var chart = astrologyTools.calculateNatalChart(
                request.birthDateTimeIso(),
                location.latitude(),
                location.longitude(),
                "P"
        );
        return new NatalChart(chart);
    }

    @Action
    CurrentTransits getCurrentTransits(GeocodedLocation location) {
        var now = Instant.now().atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        var transits = astrologyTools.calculateNatalChart(
                now, location.latitude(), location.longitude(), "P"
        );
        return new CurrentTransits(transits);
    }

    @AchievesGoal(description = "Produce a personalized daily horoscope reading")
    @Action
    HoroscopeReading interpretTransits(
            NatalChart natalChart,
            CurrentTransits currentTransits,
            OperationContext context) {
        return context.ai()
                .withLlm(LlmOptions.fromCriteria(ModelSelectionCriteria.getAuto())
                        .withTemperature(0.9))
                .createObject("""
                                You are an expert astrologer. Based on the natal chart and today's \
                                planetary transits below, write a personalized daily horoscope reading.
                                
                                Focus on the key transit-to-natal aspects:
                                - Which transiting planets are conjunct, opposite, trine, or square natal planets
                                - What houses are activated today
                                - Any notable patterns (stelliums, grand trines, T-squares)
                                
                                Keep it warm, insightful, and practical — suggest how the person might \
                                experience these energies and what to focus on today.
                                
                                ## Natal Chart
                                %s
                                
                                ## Today's Transits
                                %s
                                """.formatted(natalChart.chartData(), currentTransits.transitData()),
                        HoroscopeReading.class);
    }
}
