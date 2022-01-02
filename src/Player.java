import references.BooleanReference;
import references.IntReference;
import references.Reference;
import ui.AddSongWindow;
import ui.PlayerWindow;

import java.awt.event.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class Player {

    // Reference variables are preceded with the 'r' prefix. To access/modify
    // the value a reference variable points to, use rMyVariable.v (access the
    // 'v' field of the variable)

    // The description of the behavior of each thread can be found as a javadoc
    // of the thread's 'run' method (e.g. AddSongThread.run()).

    // data fields

    // fields used by nearly all threads
    static final Reference<PlayerWindow> rWindow = new Reference<>(null);
    // variable to count song's ID. Requires queueArrayLock
    static final IntReference rID = new IntReference(0);
    // 7 columns for all information in a song
    // (songTitle, songAlbum, songArtist, songYear, songLength, songLengthSeconds, songID)
    // requires queueArrayLock
    static final Reference<String[][]> rQueueArray = new Reference<>(new String[0][]);
    static final Reference<int[]> rRemainingSongs = new Reference<>(new int[0]);

    // used by AddSongThread. Requires addSongWindowClosedLock
    static final Reference<AddSongWindow> rNewAddSongWindow = new Reference<>(null);

    // both are used by PlayNowThread and RemoveThread.
    static final IntReference rCurrentSongID = new IntReference(-1);
    // requires nextSongLock
    static final IntReference rNextSongID    = new IntReference(-1);

    // used by PlayNowThread, PlayPauseThread and RemoveThread. Requires miniPlayerLock
    static final Reference<PlayNowThread> rCurrentMiniPlayerThread = new Reference<>(null);

    // required for changing rID.v and rQueueArray.v
    static final Lock             queueArrayLock               = new ReentrantLock();

    // required for instantiating an add song window
    static final Lock             addSongWindowClosedLock      = new ReentrantLock();
    static final Condition        addSongWindowClosedCondition = addSongWindowClosedLock.newCondition();
    static final BooleanReference rAddSongWindowClosedBool     = new BooleanReference(false);

    // required for modifying the mini player
    static final ReentrantLock    miniPlayerLock     = new ReentrantLock();
    // used to signal the current PlayNowThread. See the description of PlayNowThread.run()
    // and PlayPauseThread.run() for details.
    static final Condition        isPausedCondition  = miniPlayerLock.newCondition();
    static final Condition        isPlayingCondition = miniPlayerLock.newCondition();
    static final BooleanReference rIsPlaying         = new BooleanReference(false);
    static final BooleanReference rIsShuffle         = new BooleanReference(false);
    static final BooleanReference rIsRepeat          = new BooleanReference(false);
    static final BooleanReference rIsActive          = new BooleanReference(true);
    static final IntReference rCurrentSongTime       = new IntReference(0);

    // Required for determining which song will play after the current song finishes.
    static final Lock nextSongLock   = new ReentrantLock();


    static public void updateMiniPlayer() {
        String[] song = rQueueArray.v[rCurrentSongID.v];
        int songLength = Integer.parseInt(song[5]);
        // the fourth parameter of PlayNowThread is now the time the song should start playing
        // getScrubberValue returns the time value the user selected while dragging the mouse
        rWindow.v.updateMiniplayer(
                rIsActive.v,
                rIsPlaying.v,
                rIsRepeat.v,
                rCurrentSongTime.v,
                songLength,
                rCurrentSongID.v,
                rQueueArray.v.length);
    }


    public Player() {

        ActionListener buttonListenerPlayNow   = e ->
                new Thread(() -> {
                    miniPlayerLock.lock();
                    try {
                        rCurrentSongID.v = rWindow.v.getSelectedSongID();
                        rIsPlaying.v = true;
                        rIsActive.v = true;
                        rCurrentSongTime.v = 0;
                        new PlayNowThread().start();
                    } finally { miniPlayerLock.unlock(); }
                }).start();
        ActionListener buttonListenerRemove    = e -> new RemoveThread().start();
        ActionListener buttonListenerAddSong   = e -> new AddSongThread().start();
        ActionListener buttonListenerPlayPause = e -> new PlayPauseThread().start();
        ActionListener buttonListenerStop      = e ->
                new Thread(() -> {
                    miniPlayerLock.lock();
                    try {
                        if (rCurrentMiniPlayerThread.v != null) {
                            rCurrentMiniPlayerThread.v.interrupt();
                            rCurrentMiniPlayerThread.v = null;
                        }
                        rIsActive.v = false;
                        Player.updateMiniPlayer();
                    } finally { miniPlayerLock.unlock(); }
                }).start();
        ActionListener buttonListenerNext      = e ->
                new Thread(() -> {
                    miniPlayerLock.lock();
                    try {
                        rCurrentSongTime.v = 0;
                        rCurrentSongID.v++;
                        new PlayNowThread().start();
                    } finally { miniPlayerLock.unlock(); }
                }).start();
        ActionListener buttonListenerPrevious  = e ->
                new Thread(() -> {
                    miniPlayerLock.lock();
                    try {
                        rCurrentSongTime.v = 0;
                        rCurrentSongID.v--;
                        new PlayNowThread().start();
                    } finally { miniPlayerLock.unlock(); }
                }).start();
        // Shuffle mode should play songs randomly, but when all songs are played, it must finish the playing.
        ActionListener buttonListenerShuffle   = e ->
                new Thread(() -> {
                    // the initial value to rIsShuffle is "false", as the player starts playing in the sequential mode.
                    // we should change it as the button is pressed.
                    rIsShuffle.v = !rIsShuffle.v;

                    // if in shuffle mode (i.e., 'rIsShuffle.v = true'), update the remaining songs' array.
                    if (rIsShuffle.v) {
                        nextSongLock.lock(); // making sure the next song is not going to be selected until the rRemainingSong.v array is not updated.
                        queueArrayLock.lock(); // making sure the queueArray is not going to change so far.
                        try {
                            // this array will contain the IDs of songs not played yet.
                            rRemainingSongs.v = new int[rQueueArray.v.length];
                            for (int i = 0; i < rQueueArray.v.length; i++) {
                                rRemainingSongs.v[i] = i;
                            }
                        } finally {
                            queueArrayLock.unlock();
                            nextSongLock.unlock();
                        }
                    }
                }).start();
        ActionListener buttonListenerRepeat    = e ->
                new Thread(() -> {
                    nextSongLock.lock();
                    try { rIsRepeat.v = !rIsRepeat.v; }
                    finally { nextSongLock.unlock(); }
                }).start();

        MouseListener scrubberListenerClick = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) { }

            @Override
            public void mousePressed(MouseEvent e) {  // interrupt PlayNowThread
                new Thread(() -> {
                    if (rCurrentMiniPlayerThread.v == null) return;
                    miniPlayerLock.lock();
                    try { rCurrentMiniPlayerThread.v.interrupt(); updateMiniPlayer(); }
                    finally { miniPlayerLock.unlock(); }
                }).start();
            }

            @Override
            public void mouseReleased(MouseEvent e) {  // start new PlayNowThread
                new Thread(() -> {
                    if (rCurrentMiniPlayerThread.v == null) return;
                    rCurrentSongTime.v = rWindow.v.getScrubberValue();
                    rCurrentSongID.v = rWindow.v.getSelectedSongID();
                    new PlayNowThread().start();
                    // the fourth parameter of PlayNowThread is now the time the song should start playing
                    // getScrubberValue returns the time value the user selected when releasing the mouse button
                }).start();
            }

            @Override
            public void mouseEntered(MouseEvent e) { }

            @Override
            public void mouseExited(MouseEvent e) { }
        };

        MouseMotionListener scrubberListenerMotion = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {  // update mini player
                if (rCurrentMiniPlayerThread.v == null) return;
                new Thread(Player::updateMiniPlayer).start();
            }

            @Override
            public void mouseMoved(MouseEvent e) { }
        };

        String windowTitle = "Spotifraco";

        rWindow.v = new PlayerWindow(
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerPrevious,
                buttonListenerShuffle,
                buttonListenerRepeat,
                scrubberListenerClick,
                scrubberListenerMotion,
                windowTitle,
                rQueueArray.v
        );
    }
}
