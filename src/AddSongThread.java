import references.BooleanReference;
import references.IntReference;
import references.Reference;

import ui.AddSongWindow;
import ui.PlayerWindow;

import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AddSongThread extends Thread{

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
    static final IntReference rID = Player.rID;  // requires queueArrayLock
    // requires addSongWindowClosedLock
    static final Reference<AddSongWindow> rNewAddSongWindow = Player.rNewAddSongWindow;

    static final Reference<PlayNowThread> rCurrentMiniPlayerThread = Player.rCurrentMiniPlayerThread;
    static final BooleanReference rIsShuffle         = Player.rIsShuffle;
    static final Reference<int[]> rRemainingSongs = Player.rRemainingSongs;

    // fields for locks, conditions and associated booleans
    static final Lock queueArrayLock = Player.queueArrayLock;
    static final Lock addSongWindowClosedLock = Player.addSongWindowClosedLock;
    static final Condition addSongWindowClosedCondition = Player.addSongWindowClosedCondition;
    static final BooleanReference rAddSongWindowClosedBool = Player.rAddSongWindowClosedBool;

    static final ReentrantLock miniPlayerLock = Player.miniPlayerLock;

    static final Lock nextSongLock = Player.nextSongLock;

    // private fields
    private static final ActionListener buttonListener = e -> { // add song control
        // get new song from the add rWindow interface
        String[] newSong = rNewAddSongWindow.v.getSong();

        // add the new song to the playlist
        String[][] newPlaylist = new String[rQueueArray.v.length + 1][7]; // creating new playlist with one more row
        System.arraycopy(rQueueArray.v, 0, newPlaylist, 0, rQueueArray.v.length); // copying previous songs to the new playlist
        newPlaylist[rQueueArray.v.length] = newSong; // adding new song to the new playlist

        rID.v++; // update rID

        // updating playlist
        rWindow.v.updateQueueList(newPlaylist);
        rQueueArray.v = newPlaylist;

        /////////
    };

    private static final WindowListener onAddSongWindowClosed = new WindowListener() {
        @Override
        public void windowOpened(WindowEvent e) {}

        @Override
        public void windowClosing(WindowEvent e) {}

        @Override
        public void windowClosed(WindowEvent e) {
            addSongWindowClosedLock.lock();
            try {
                rAddSongWindowClosedBool.v = true;
                addSongWindowClosedCondition.signalAll();
            } finally {
                addSongWindowClosedLock.unlock();
            }
        }

        @Override
        public void windowIconified(WindowEvent e) {}

        @Override
        public void windowDeiconified(WindowEvent e) {}

        @Override
        public void windowActivated(WindowEvent e) {}

        @Override
        public void windowDeactivated(WindowEvent e) {}
    };

    /**
     * Acquire queueArrayLock (required for modifying rID.v and rQueueArray.v)
     * and only release it when the user has finished (or canceled) adding a song.
     */

    static public int[] addElement(int[] Array, int element) {
        int[] anotherArray = new int[Array.length + 1];

        System.arraycopy(Array, 0, anotherArray, 0, Array.length);
        anotherArray[Array.length] = element;

        return anotherArray;
    }

    public void run() {
        // critical section: update the playlist with a new song
        queueArrayLock.lock(); // only unlock when the Add song rWindow closes
        addSongWindowClosedLock.lock();
        try {
            rAddSongWindowClosedBool.v = false;
            // calling the add song rWindow
            rNewAddSongWindow.v = new AddSongWindow(
                    Integer.toString(rID.v),
                    buttonListener,
                    rWindow.v.getAddSongWindowListener(),
                    onAddSongWindowClosed
            );

            while (!rAddSongWindowClosedBool.v) {
                // addSongWindowClosedLock is automatically released while waiting
                addSongWindowClosedCondition.await();
                // the signalAll() is in a method of this.onAddSongWindowClosed
            }

            // if in shuffle mode, we should add the new song ID to the rRemainingSong.v array,
            // but we got to make sure we have control over the next song to play.
            if (rIsShuffle.v) {
                nextSongLock.lock();
                try {
                    rRemainingSongs.v = addElement(rRemainingSongs.v, rID.v - 1);
                } finally {
                    nextSongLock.unlock();
                }
            }

            // addSongWindowClosedLock is automatically re-acquired when the wait terminates
            // the exception will never actually occur, but await() throws it, so it must be caught
        } catch (InterruptedException ignored) { }
          finally {
            addSongWindowClosedLock.unlock();
            queueArrayLock.unlock();
        }
        // end of critical section

        // update the mini player to reflect the new size of the playlist
        miniPlayerLock.lock();
        try {
            // if it evaluates to true, we need it to stay true until the whole
            // operation ends; that's why this section needs to be locked.
            // if there is a song in the mini player
            if (rCurrentMiniPlayerThread.v != null) Player.updateMiniPlayer();
        } finally { miniPlayerLock.unlock(); }
    }
}
