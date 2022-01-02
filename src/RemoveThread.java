import references.BooleanReference;
import references.IntReference;
import references.Reference;
import ui.PlayerWindow;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RemoveThread extends Thread {

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
    static final Reference<PlayerWindow> rWindow   = Player.rWindow;
    static final Reference<String[][]> rQueueArray = Player.rQueueArray;  // requires queueArrayLock
    static final IntReference rID = Player.rID;  // requires queueArrayLock

    // fields relevant to PlayNowThread
    static final IntReference rCurrentSongID = Player.rCurrentSongID;  // not overwritten
    static final IntReference rNextSongID    = Player.rNextSongID;     // requires nextSongLock
    static final BooleanReference rIsShuffle         = new BooleanReference(false);
    static final IntReference rCurrentSongTime = Player.rCurrentSongTime;
    // interrupting requires miniPlayerLock
    static final Reference<PlayNowThread> rCurrentMiniPlayerThread = Player.rCurrentMiniPlayerThread;

    static final Reference<int[]> rRemainingSongs = Player.rRemainingSongs;

    // fields for locks, conditions and associated booleans
    static final Lock queueArrayLock = Player.queueArrayLock;
    static final Lock nextSongLock = Player.nextSongLock;

    static final ReentrantLock miniPlayerLock = Player.miniPlayerLock;

    private void updatePlayNowThread(int removedSong) {
        if (removedSong < rCurrentSongID.v) {
            miniPlayerLock.lock();
            try { rCurrentSongID.v--; rNextSongID.v--;}
            finally { miniPlayerLock.unlock(); }
        } else if (removedSong == rCurrentSongID.v) {
            // if the removed song is the song currently in the miniplayer, put
            // the next song in the miniplayer, i.e., start a new PlayNowThread
            // with the next song.
            // wait until the current PlayNowThread is asleep
            rCurrentSongTime.v = 0;
            new PlayNowThread().start();
        }
    }

    static public int[] removeElement(int[] Array, int element) {
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
     * Remove the selected song from the playlist and update the playlist.
     * If there is a song currently playing, and the removed song comes
     * before the currently playing song, acquire miniPlayerLock and then
     * decrement both rCurrentSongID.v and rNextSongID. If there is a song
     * currently playing, and the removed song *is* the currently playing
     * song, start a new PlayNowThread for playing the song that got the
     * place of the removed song.
     */
    public void run() {
        // critical section: update the playlist with a removed song
        int removedSongID;
        if(queueArrayLock.tryLock()) {
            try {
                // getting information of the selected song
                removedSongID = rWindow.v.getSelectedSongID();

                // if in shuffle mode, we should remove the selected song's ID from the rRemainingSong.v array,
                // but we got to make sure we have control over the next song to play.
                if (rIsShuffle.v) {
                    nextSongLock.lock();
                    try {
                        if (checkElement(rRemainingSongs.v, rCurrentSongID.v)) {
                            rRemainingSongs.v = removeElement(rRemainingSongs.v, removedSongID);
                        }
                    } finally {
                        nextSongLock.unlock();
                    }
                }

                // creating new playlist with one less row
                String[][] newPlaylist = new String[rQueueArray.v.length - 1][7];

                // copying all but the selected song to the new playlist
                for (int i = 0; i < rID.v; i++) {
                    if (i < removedSongID) { // add songs that come before the removed song
                        newPlaylist[i] = rQueueArray.v[i];
                    } else if (i > removedSongID) {
                        // add songs that come after the removed song, but with an offset
                        newPlaylist[i - 1] = rQueueArray.v[i];
                        newPlaylist[i - 1][6] = Integer.toString(Integer.parseInt(
                                newPlaylist[i - 1][6]) - 1); // update rID of songs
                    }
                }
                // updating playlist
                rQueueArray.v = newPlaylist;
                rWindow.v.updateQueueList(newPlaylist);
                rID.v--; // update rID

            } finally { queueArrayLock.unlock(); }

            // update the thread affected by the change
            if (rCurrentMiniPlayerThread.v != null)  // if there is a song in the miniplayer
                updatePlayNowThread(removedSongID);
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
}
