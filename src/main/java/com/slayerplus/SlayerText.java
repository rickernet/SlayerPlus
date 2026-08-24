package com.slayerplus;
import java.util.Locale;
final class SlayerText{private SlayerText(){}static String normalize(String value){return value==null?"":value.toLowerCase(Locale.ENGLISH).replace('\u2019','\'').replaceAll("[^a-z0-9]+"," ").trim();
}static String encounter(String value){return normalize(value).replaceFirst("^the\\s+","");
}static String plusEncounter(String value){return encounter(value==null?null:value.replace("+"," plus "));
}}
