package com.fp.foodorderingsystem.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DateUtil {
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String DISPLAY_FORMAT = "MMM dd, yyyy 'at' HH:mm";
    
    public static String formatDate(String dateString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat(DISPLAY_FORMAT, Locale.getDefault());
            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (ParseException e) {
            return dateString;
        }
    }
    
    public static String getTimeAgo(String dateString) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
            Date date = format.parse(dateString);
            long diff = System.currentTimeMillis() - date.getTime();
            
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            if (minutes < 60) {
                return minutes + " minutes ago";
            }
            
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            if (hours < 24) {
                return hours + " hours ago";
            }
            
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            return days + " days ago";
        } catch (ParseException e) {
            return dateString;
        }
    }
}

