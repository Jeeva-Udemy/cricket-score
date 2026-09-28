package com.example.cricketscorer.stats

import com.example.cricketscorer.model.DismissedEnd

/**
 * Who is at which end after a wicket — kept as plain, testable logic (used by
 * ScoringViewModel.applyDelivery and the Wicket dialog's "next ball" preview).
 *
 * Run-out fix: previously a wicket always put the new batsman in the dismissed batsman's
 * old slot and ignored any runs completed before the run-out, and the dialog defaulted to
 * "striker is out" behind an easy-to-miss swap icon — so e.g. "ran 2, non-striker run out
 * going for the 3rd" was easily recorded against the striker. Now the scorer picks the out
 * batsman by name, and chooses which end the new batsman comes in at (with a sensible
 * default based on the runs completed).
 */
object CreasePositions {

    data class Crease(
        val strikerName: String,
        val strikerNumber: Int,
        val nonStrikerName: String,
        val nonStrikerNumber: Int
    )

    /**
     * Default end for the incoming batsman on a run-out: the end the dismissed batsman was
     * heading to / standing at after [runsCompleted] completed runs (an odd number of runs
     * means the batsmen have crossed). For every other dismissal the new batsman simply
     * takes the striker's end.
     */
    fun defaultIncomingAtStrikerEnd(isRunOut: Boolean, dismissedEnd: DismissedEnd, runsCompleted: Int): Boolean {
        if (!isRunOut) return true
        val crossed = runsCompleted % 2 == 1
        return (dismissedEnd == DismissedEnd.STRIKER) != crossed
    }

    /**
     * Crease after a wicket ball, BEFORE any end-of-over swap (the caller applies that).
     * [dismissedEnd] refers to the batsman's role when the ball was bowled (STRIKER = the one
     * who faced it). The surviving batsman goes to whichever end the new batsman doesn't.
     */
    fun afterWicket(
        before: Crease,
        dismissedEnd: DismissedEnd,
        incomingName: String,
        incomingNumber: Int,
        incomingAtStrikerEnd: Boolean
    ): Crease {
        val survivorName = if (dismissedEnd == DismissedEnd.STRIKER) before.nonStrikerName else before.strikerName
        val survivorNumber = if (dismissedEnd == DismissedEnd.STRIKER) before.nonStrikerNumber else before.strikerNumber
        return if (incomingAtStrikerEnd) {
            Crease(incomingName, incomingNumber, survivorName, survivorNumber)
        } else {
            Crease(survivorName, survivorNumber, incomingName, incomingNumber)
        }
    }

    fun swapEnds(c: Crease): Crease = Crease(c.nonStrikerName, c.nonStrikerNumber, c.strikerName, c.strikerNumber)
}
