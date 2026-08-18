package com.example.cricketscorer.model

/**
 * Decision made by the toss-winning captain.
 */
enum class TossDecision {
    BAT,
    BOWL
}

/**
 * Type of extra conceded on a delivery.
 * WIDE / NO_BALL -> illegal deliveries, do NOT count towards the 6 balls of an over.
 * BYE / LEG_BYE  -> legal deliveries, DO count towards the over; runs are not credited
 *                   to the batsman but are added to the team total.
 */
enum class ExtraType {
    NONE,
    WIDE,
    NO_BALL,
    BYE,
    LEG_BYE
}

/**
 * How a batsman got out.
 */
enum class WicketType {
    NONE,
    BOWLED,
    CAUGHT,
    LBW,
    RUN_OUT,
    STUMPED,
    HIT_WICKET
}
