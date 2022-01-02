package ui.support;

public class SecondsToString {

    @SuppressWarnings("DuplicatedCode")
    public static String lengthToString(int length) {
        if (length < 0) return "--:--";
        String hours;
        String minutes;
        String seconds;
        int remainingMinutes;
        int remainingSeconds;
        int remainingHours;
        StringBuilder stringBuilder = new StringBuilder();
        if (length < 60) {
            // From 00:00 to 00:59
            seconds = stringBuilder.append(length).toString();
            stringBuilder.setLength(0);
            return stringBuilder.append(seconds).append("s").toString();
        } else if (length < 3600) {
            // From 01:00 to 59:59
            // Seconds ->
            remainingSeconds = length % 60;
            if (remainingSeconds < 10) {
                // If 01:00 or 59:09 for example
                seconds = stringBuilder.append("0").append(remainingSeconds).toString();
            } else {
                // If 01:10 or 59:59 for example
                seconds = stringBuilder.append(remainingSeconds).toString();
            }
            stringBuilder.setLength(0);
            // Minutes ->
            // Minutes will be displayed as is, without a 0 at the start if it's 9:59 for example
            remainingMinutes = length / 60;
            minutes = stringBuilder.append(remainingMinutes).toString();
            stringBuilder.setLength(0);
            return stringBuilder.append(minutes).append(":").append(seconds).toString();
        } else {
            // More than 59:59
            // Seconds ->
            remainingSeconds = length % 60;
            if (remainingSeconds < 10) {
                // If 1:00:00 or 1:59:09 for example
                seconds = stringBuilder.append("0").append(remainingSeconds).toString();
            } else {
                // If 1:00:10 or 1:59:59 for example
                seconds = stringBuilder.append(remainingSeconds).toString();
            }
            stringBuilder.setLength(0);
            // Minutes ->
            remainingMinutes = length / 60;
            if (remainingMinutes % 60 < 10) {
                // If 1:00:00 or 1:09:59 for example
                minutes = stringBuilder.append("0").append(remainingMinutes % 60).toString();
            } else {
                // If 1:10:00 or 1:59:59 for example
                minutes = stringBuilder.append(remainingMinutes % 60).toString();
            }
            stringBuilder.setLength(0);
            // Hours ->
            remainingHours = remainingMinutes / 60;
            if (remainingHours < 10) {
                // If 1:00:00 or 9:59:59 for example
                hours = stringBuilder.append("0").append(remainingMinutes / 60).toString();
            } else {
                // If 10:00:00 or 99:59:59 for example
                hours = stringBuilder.append(remainingMinutes / 60).toString();
            }
            stringBuilder.setLength(0);
            return stringBuilder.append(hours).append(":").append(minutes).append(":").append(seconds).toString();
        }
    }

    public static String currentTimeToString(int songCurrentTime, int songTotalTime) {
        if (songCurrentTime > songTotalTime | songCurrentTime < 0) {
            return "--:--";
        }

        StringBuilder stringBuilder = new StringBuilder();
        if (songTotalTime < 60) {
            // Up to 59 seconds
            return stringBuilder.append(lengthToString(songCurrentTime)).toString();
        } else if (songTotalTime < 600) { // 600 seconds = 10:00 while 599 = 9:59
            // Up to 9:59
            if (songCurrentTime < 10) {
                // 0 to 9 seconds remaining
                return stringBuilder.append("0:0").append(songCurrentTime).toString();
            } else if (songCurrentTime < 60) {
                // 10 to 59 seconds remaining
                return stringBuilder.append("0:").append(songCurrentTime).toString();
            } else { // 600 seconds = 10:00 while 599 = 9:59
                // From 01:00 up to 09:59
                return lengthToString(songCurrentTime);
            }
        } else if (songTotalTime < 3600) {
            // Up to 59:59
            if (songCurrentTime < 10) {
                // 0 to 9 seconds remaining
                return stringBuilder.append("00:0").append(songCurrentTime).toString();
            } else if (songCurrentTime < 60) {
                // 10 to 59 seconds remaining
                return stringBuilder.append("00:").append(songCurrentTime).toString();
            } else if (songCurrentTime < 600) { // 600 seconds = 10:00 while 599 = 9:59
                // From 01:00 up to 09:59
                return stringBuilder.append("0").append(lengthToString(songCurrentTime)).toString();
            } else {
                // From 10:00 up to 59:59
                return lengthToString(songCurrentTime);
            }
        } else {
            // More than 59:59
            if (songCurrentTime < 10) {
                // 0 to 9 seconds remaining
                return stringBuilder.append("00:00:0").append(songCurrentTime).toString();
            } else if (songCurrentTime < 60) {
                // 10 to 59 seconds remaining
                return stringBuilder.append("00:00:").append(songCurrentTime).toString();
            } else if (songCurrentTime < 600) { // 600 seconds = 10:00 while 599 = 9:59
                // From 01:00 up to 09:59
                return stringBuilder.append("00:0").append(lengthToString(songCurrentTime)).toString();
            } else if (songCurrentTime < 3600) {
                // From 10:00 up to 59:59
                return stringBuilder.append("00:").append(lengthToString(songCurrentTime)).toString();
            } else {
                // More than 59:59
                return lengthToString(songCurrentTime);
            }
        }
    }
}
