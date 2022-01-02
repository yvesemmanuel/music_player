import references.BooleanReference;
import references.IntReference;
import references.Reference;
import ui.PlayerWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ThreadLocalRandom;

public class PlayNowThread extends Thread {

    // static final fields contain the data about the Player that the thread
    // uses. They are set from Player's constructor.
    // In general, these fields contain relevant control information about
    // the Player as well as control information for inter-thread communication
    // and synchronization, such as locks, conditions and others.

    // Most static final fields contain data concerning the Player that is
    // shared with other threads. In such cases, the appropriate lock must be
    // acquired before overwriting the field.

    // Non-static fields, as well as constant fields (i.e. static final fields)
    // and private fields contain local data that the thread uses for
    // self-management. Non-final static fields are usually initialized
    // on instantiation of the class, which happens in one of the Player.rWindow's
    // ActionListeners.

    // Non-static fields are appended with 'this.' to differentiate them from static
    // fields. Thus, you can assume that field references that are not preceded by
    // 'this.' are used for inter-thread communication and/or updating the Player.

    // If a variable is a reference variable, it is safe to assume that you should
    // acquire its corresponding lock before overwriting its value

    // fields for general Player data
    static final Reference<PlayerWindow> rWindow = Player.rWindow;
    static final Reference<String[][]> rQueueArray = Player.rQueueArray;  // requires queueArrayLock
    static final Reference<int[]> rRemainingSongs = Player.rRemainingSongs;
    static final IntReference rID = Player.rID;          // requires queueArrayLock

    // field relevant to this thread, RemoveThread and PlayPauseThread
    // requires miniPlayerLock
    static final Reference<PlayNowThread> rCurrentMiniPlayerThread = Player.rCurrentMiniPlayerThread;

    // fields relevant to the mini player
    static final IntReference rCurrentSongID   = Player.rCurrentSongID;  // not overwritten
    static final IntReference rNextSongID      = Player.rNextSongID;     // requires nextSongLock
    static final IntReference rCurrentSongTime = Player.rCurrentSongTime;
    // boolean associated with isPlayingCondition.
    static final BooleanReference rIsPlaying = Player.rIsPlaying; // Requires miniPlayerLock
    static final BooleanReference rIsShuffle = Player.rIsShuffle;
    static final BooleanReference rIsRepeat  = Player.rIsRepeat;

    // fields for locks, conditions and associated booleans
    static final Lock queueArrayLock = Player.queueArrayLock;
    static final Lock nextSongLock = Player.nextSongLock;
    static final ReentrantLock miniPlayerLock = Player.miniPlayerLock;
    static final Condition isPausedCondition  = Player.isPausedCondition;
    static final Condition isPlayingCondition = Player.isPlayingCondition;

    // See description of run() below.

    private void waitOnePlayingSecond() throws InterruptedException {
        // a playing second is defined as a second during which the
        // current song is not paused.
        long sleepFor = 1000;
        while (sleepFor > 0) {
            boolean paused = !rIsPlaying.v;
            Instant sleepStartInstant = Instant.now();
            if(!paused) {
                // the thread releases the lock automatically on sleeping
                try { paused = !isPausedCondition.await(sleepFor, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ex) { throw new InterruptedException(); }
                // the thread re-acquires the lock automatically on awakening
            }
            long sleptFor = Duration.between(sleepStartInstant, Instant.now()).toMillis();
            // if the song was paused, this will be greater than zero
            sleepFor = sleepFor - sleptFor;
            if (paused) {
                // then we must wait for the song to start again.
                // Notice that waiting during a pause is not accounted as
                // sleep time (doesn't change sleptFor).

                // wait until it is playing again
                // the thread releases the lock automatically on sleeping
                try { while (!rIsPlaying.v) isPlayingCondition.await(); }
                // the thread re-acquires the lock automatically on awakening
                catch (InterruptedException ex) { throw new InterruptedException(); }
            }
        }
    }

    public int[] removeElement(int[] Array, int element) {
        if (Array.length == 0) {
            return Array;
        }

        int[] anotherArray = new int[Array.length - 1];

        for (int i = 0, k = 0; i < Array.length; i++) {
            if (Array[i] == element) {
                continue;
            }
            anotherArray[k++] = Array[i];
        }

        return anotherArray;
    }

    public boolean checkElement(int[] Array, int element) {
        for (int x: Array) {
            if (x == element) {
                return true;
            }
        }
        return false;
    }

   /**
    * If there is another currently running
    * PlayNowThread, interrupt it when it reaches a wait
    * state (i.e. when it releases the miniPlayerLock).
    * Put the song with ID r.CurrentSongID.v on the mini player and start
    * playing it if this.isPlaying_ is true. When a song finishes,
    * start the next one if there is a next one, or disable the mini
    * player and return if there isn't. If the song is paused, wait
    * until it unpauses. During periods when the scrubber is not being
    * updated (one-second intervals), release the miniPlayerLock.
    */
    public void run() {  // Game of Threads
        // critical region: update the mini player every second.
        // During the one-second intervals, as well as in the intervals
        // during which the song is paused, the lock is released.
        miniPlayerLock.lock();  // the lock is also required for calling methods
                                // on rWindow.v that change the mini player.
        try {
            // substitute the other PlayNowThread if it is still running
            if (rCurrentMiniPlayerThread.v != null) rCurrentMiniPlayerThread.v.interrupt();
            rCurrentMiniPlayerThread.v = (PlayNowThread) Thread.currentThread();

            if (rID.v == 0 || rCurrentSongID.v == rID.v) {
                // then there are no songs in the playlist
                // or the last song has finished playing
                rWindow.v.resetMiniPlayer();
                rWindow.v.disableScrubberArea();
                rCurrentMiniPlayerThread.v = null;
                return;
            }

            rWindow.v.enableScrubberArea();

            do { // main loop of a PlayNowThread
                // get and display current song information
                String[] song = rQueueArray.v[rCurrentSongID.v];
                int songLength = Integer.parseInt(song[5]);
                rWindow.v.updatePlayingSongInfo(song[0], song[1], song[2]);

                while (rCurrentSongTime.v <= songLength) { // loop of a single song
                    System.out.println(rQueueArray.v.length);
                    // display current status of the song
                    rWindow.v.updatePlayPauseButton(true);
                    Player.updateMiniPlayer();

                    // wait one second while the music is playing.
                    // the thread releases the lock while waiting.
                    try { waitOnePlayingSecond(); }
                    catch (InterruptedException ex) { return; }

                    rCurrentSongTime.v++;
                }

                // set base value for the next song. It may be changed
                // if a song is removed while the current is playing.
                nextSongLock.lock();
                try {
                    if (rIsRepeat.v) rNextSongID.v = rCurrentSongID.v;
                    else if (rIsShuffle.v) { // SHUFFLE MODE

                        // if the song that just played is already in the remaining songs, remove it.
                        if (checkElement(rRemainingSongs.v, rCurrentSongID.v)) {
                            rRemainingSongs.v = removeElement(rRemainingSongs.v, rCurrentSongID.v);
                        }

                        if (rRemainingSongs.v.length == 0) break; // no more songs left, that's the end of shuffle mode (stop the player).

                        // if we still got songs left,
                        // choose a random index to select a remaining song in rRemainingSong.v
                        // random value between [0, rRemainingSong.v.length)
                        int randomIndex = ThreadLocalRandom.current().nextInt(rRemainingSongs.v.length);
                        rNextSongID.v = rRemainingSongs.v[randomIndex];

                    } else { // SEQUENTIAL MODE
                        rNextSongID.v = rCurrentSongID.v + 1;
                    }
                }
                finally { nextSongLock.unlock(); }

                rCurrentSongID.v = rNextSongID.v;
                rCurrentSongTime.v = 0; // play next song from beginning

            } while (rCurrentSongID.v < rID.v); // we got to the end of the rID.v array, that's the end of sequential mode.

            // the last song has finished playing
            rIsPlaying.v = false;
            rWindow.v.resetMiniPlayer();
            rWindow.v.disableScrubberArea();
            rCurrentMiniPlayerThread.v = null;

            // if still in shuffle mode (i.e. 'rIsShuffle = true'), update the rRemainingSong.v array,
            // because the player should still choose the next song to play randomly.
            if (rIsShuffle.v) {
                queueArrayLock.lock();
                try {
                    // this array will contain the IDs of songs not played yet.
                    rRemainingSongs.v = new int[rQueueArray.v.length];
                    for (int i = 0; i < rQueueArray.v.length; i++) {
                        rRemainingSongs.v[i] = i;
                    }
                } finally {
                    queueArrayLock.unlock();
                }
            }

        } finally {
            if(miniPlayerLock.isHeldByCurrentThread()) miniPlayerLock.unlock();
        }
    }

}
