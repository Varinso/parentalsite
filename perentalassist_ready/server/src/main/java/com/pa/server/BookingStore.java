package com.pa.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Minimal, safe appointment storage using a CSV file:
 *   <user.home>/.perentalassist/appointments.csv
 * Columns: id,doctorId,userId,date(YYYY-MM-DD),time(HH:mm),durationMin,videoUrl
 */
public class BookingStore {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Path dataDir;
    private final Path file;

    public BookingStore() {
        this.dataDir = Paths.get(System.getProperty("user.home"), ".perentalassist");
        this.file = dataDir.resolve("appointments.csv");
        try {
            Files.createDirectories(dataDir);
            if (!Files.exists(file)) {
                Files.writeString(file, "id,doctorId,userId,date,time,durationMin,videoUrl\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
        } catch (IOException ignored) {}
    }

    public synchronized List<String> getBookedTimes(String doctorId, LocalDate date) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>();
            for (String ln : lines) {
                if (ln.startsWith("id,")) continue;
                String[] p = ln.split(",", -1);
                if (p.length < 7) continue;
                String dId = p[1];
                LocalDate d = LocalDate.parse(p[3], DATE);
                if (doctorId.equals(dId) && date.equals(d)) {
                    out.add(p[4]); // time
                }
            }
            return out;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public synchronized boolean isBooked(String doctorId, LocalDate date, LocalTime time) {
        String t = TIME.format(time);
        return getBookedTimes(doctorId, date).contains(t);
    }

    public synchronized String book(String doctorId, String userId, LocalDate date, LocalTime time, int durationMin, String videoUrl) throws IOException {
        String id = String.valueOf(System.currentTimeMillis());
        String line = String.join(",",
                id,
                escape(doctorId),
                escape(userId),
                DATE.format(date),
                TIME.format(time),
                String.valueOf(durationMin),
                escape(videoUrl)
        ) + "\n";
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        return id;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace(",", " ");
    }

    /** Default schedule: Mon–Fri 09:00–17:00, 30-minute slots. You can extend to read per-doctor config if needed. */
    public List<String> computeAvailableSlots(String doctorId, LocalDate date, int slotMinutes, List<String> alreadyBooked) {
        DayOfWeek dow = date.getDayOfWeek();
        // default schedule: Mon–Fri 09:00–17:00 ; Sat/Sun closed
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return Collections.emptyList();

        LocalTime start = LocalTime.of(9, 0);
        LocalTime end   = LocalTime.of(17, 0);

        List<String> slots = new ArrayList<>();
        LocalTime t = start;
        while (!t.isAfter(end.minusMinutes(slotMinutes))) {
            String s = TIME.format(t);
            if (!alreadyBooked.contains(s)) slots.add(s);
            t = t.plusMinutes(slotMinutes);
        }
        return slots;
    }
}
