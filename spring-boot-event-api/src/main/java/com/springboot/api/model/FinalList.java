package com.springboot.api.model;

import java.util.List;
import java.util.Map;

public class FinalList {

    private Map<String,List<Invitation>> countries;

    public Map<String, List<Invitation>> getCountries() {
        return countries;
    }

    public void setCountries(Map<String, List<Invitation>> countries) {
        this.countries = countries;
    }



}
