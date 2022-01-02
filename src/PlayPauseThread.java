import references.BooleanReference;
import references.Reference;
import ui.PlayerWindow;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PlayPauseThread extends Thread {

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

    // field for general Player data
    static final Reference<PlayerWindow> rWindow = Player.rWindow;

    // field relevant to PlayNowThread
    static final BooleanReference rIsPlaying = Player.rIsPlaying;  // requires miniPlayerLock

    // fields for locks, conditions and associated booleans
    static final ReentrantLock miniPlayerLock = Player.miniPlayerLock;
    static final Condition isPausedCondition  = Player.isPausedCondition;
    static final Condition isPlayingCondition = Player.isPlayingCondition;


    public PlayPauseThread() { }

    /**
     * Wait until miniPlayerLock is acquired (the lock for modifying the
     * mini player). Then, if the user paused the song, update the mini
     * player and signal to the current PlayNowThread that it should wait
     * for the song to unpause. Else (i.e. if the user unpaused the song),
     * update the mini player and signal to the current PlayNowThread that
     * it can stop waiting for the song to unpause and return its activity.
     */
    public void run() {
        miniPlayerLock.lock();
        try {
            rIsPlaying.v = !rIsPlaying.v;
            rWindow.v.updatePlayPauseButton(rIsPlaying.v);
            if (rIsPlaying.v) isPlayingCondition.signalAll();
            else                 isPausedCondition.signalAll();
        } finally { miniPlayerLock.unlock(); }
    }

}
