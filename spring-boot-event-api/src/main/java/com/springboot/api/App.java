package com.springboot.api;

import static com.springboot.api.utils.Constants.DATE_FORMAT;
import static com.springboot.api.utils.Constants.DIFF_IN_DAYS;
import static com.springboot.api.utils.Constants.dataSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.springboot.api.model.FinalList;
import com.springboot.api.model.Invitation;
import com.springboot.api.model.Partner;

import com.google.gson.Gson;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public final class App {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

    private App() {
    }

    /**
     * 
     * @param args The arguments of the program.
     * @throws IOException
     * @throws JSONException
     * @throws ParseException
     */
    public static void main(String[] args) throws IOException, ParseException {

        // Connect to the URL using java's native library
        URL url = new URL(dataSet);
        URLConnection request = url.openConnection();
        request.connect();

        JSONParser parser = new JSONParser();
        // Convert the input stream to a json element
        JSONObject obj = (JSONObject) parser.parse(new InputStreamReader((InputStream) request.getContent()));
        JSONArray jsonObj = (JSONArray) obj.get("partners");

        // Extracting arrays from the JSON object and create partners List
        List<Partner> partnersList = new ArrayList<>();
        Partner partner = new Partner();

        for (Object o : jsonObj) {

            JSONObject person = (JSONObject) o;
            partner = new Partner();

            partner.setFirstName(person.get("firstName").toString());
            partner.setFirstName(person.get("lastName").toString());
            partner.setCountry(person.get("country").toString());
            partner.setEmail(person.get("email").toString());

            JSONArray datelst = (JSONArray) person.get("availableDates");
            List<String> dates = new ArrayList<>();
            for (Object d : datelst) {
                dates.add(d.toString());
            }
            partner.setAvailableDates(dates);
            partnersList.add(partner);
        }

        Map<String, List<Invitation>> invitationMap = checkAvailableDatesAndGetInvitations(partnersList);

        String json = new Gson().toJson(invitationMap);      
        // final invitation list json
        System.out.println(json);
    }

    public static Map<String, List<Invitation>> checkAvailableDatesAndGetInvitations(List<Partner> partnersList) {

        Map<String, String> countryToDateMap = new HashMap<>();

        // map country and it's index position in partners list.
        Map<String, List<Integer>> countriesToIndexMap = new HashMap<>();
        for (int i = 0; i < partnersList.size(); i++) {
            String country = partnersList.get(i).getCountry();
            List<Integer> indexes = countriesToIndexMap.getOrDefault(country, new ArrayList<>());
            indexes.add(i);
            countriesToIndexMap.put(country, indexes);
        }

        for (String country : countriesToIndexMap.keySet()) {
            List<String> datesList = new ArrayList<>();

            // loop through mapped index position for each country to get dates list.
            for (int index : countriesToIndexMap.get(country)) {
                List<String> dates = partnersList.get(index).getAvailableDates();

                for (int j = 0; j < dates.size() - 1; j++) {
                    if (getDiffInDates(dates.get(j), dates.get(j + 1)) == DIFF_IN_DAYS) {
                        datesList.add(dates.get(j));
                    }
                }
            }

            // check the duplicates strings in the dates list and add it's count to a map.
            HashMap<String, Integer> datesCountMap = new HashMap<>();
            for (String dateStr : datesList) {
                datesCountMap.put(dateStr, datesCountMap.getOrDefault(dateStr, 0) + 1);
            }

            // get maximum dates count from map.
            int maxDateCount = Collections.max(datesCountMap.values());

            // get list of dates which has maximum count.
            List<String> similarCountDatesList = new ArrayList<>();
            for (String mapDateStr : datesCountMap.keySet()) {
                if (datesCountMap.get(mapDateStr) == maxDateCount) {
                    similarCountDatesList.add(mapDateStr);
                }
            }

            // sort similar dates and get the lowest date available from max count.
            Collections.sort(similarCountDatesList);

            if (similarCountDatesList != null && similarCountDatesList.size() > 0) {
                countryToDateMap.put(country, similarCountDatesList.get(0));
            } else {
                countryToDateMap.put(country, null);
            }

        }

        return generateInvitationRequestObj(countryToDateMap, partnersList, countriesToIndexMap);

    }

    public static HashMap<String, List<Invitation>> generateInvitationRequestObj(Map<String, String> countryToDateMap,
            List<Partner> partnersList, Map<String, List<Integer>> countriesToIndexMap) {

        List<Invitation> invitationsList = new ArrayList<>();

        for (String country : countriesToIndexMap.keySet()) {
            int attendeeCount = 0;
            Set<String> attendeesList = new HashSet<>();

            for (int i : countriesToIndexMap.get(country)) {

                List<String> datesList = partnersList.get(i).getAvailableDates();
                if (datesList.contains(countryToDateMap.get(country))) {
                    Date currDate = null;

                    try {
                        currDate = dateFormat.parse(countryToDateMap.get(country));
                    } catch (java.text.ParseException e) {
                        System.out.println("Unable to parse date in countryToDateMap: " + e.getMessage());
                    }

                    Calendar cal1 = Calendar.getInstance();
                    cal1.setTime(currDate);
                    cal1.add(Calendar.DATE, DIFF_IN_DAYS);

                    Date nextDay = cal1.getTime();
                    String nextDayStr = dateFormat.format(nextDay);

                    if (datesList.contains(nextDayStr)) {
                        attendeesList.add(partnersList.get(i).getEmail());
                        attendeeCount++;
                    }
                }
            }

            Invitation invitation = new Invitation();

            invitation.setStartDate(countryToDateMap.get(country));
            invitation.setName(country);
            invitation.setAttendeeCount(attendeeCount);
            List<String> attendeesListArray = new ArrayList<>(attendeesList);
            invitation.setAttendees(attendeesListArray);

            invitationsList.add(invitation);
        }
        FinalList finalLst = new FinalList();
        HashMap<String, List<Invitation>> countries = new HashMap<>();
        countries.put("countries", invitationsList);
        finalLst.setCountries(countries);

        List<FinalList> resultLst = new ArrayList<>();
        resultLst.add(finalLst);

        return countries;
    }

    private static long getDiffInDates(String startDateStr, String endDateStr) {
        long diff = 0;
        try {
            Date startDate = dateFormat.parse(startDateStr);
            Date endDate = dateFormat.parse(endDateStr);

            long diffInMilliSeconds = Math.abs(endDate.getTime() - startDate.getTime());
            diff = TimeUnit.DAYS.convert(diffInMilliSeconds, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            System.out.println("Exception when comparing dates: " + ex.getMessage());
        }
        return diff;
    }

}
