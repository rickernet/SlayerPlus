package com.slayerplus;
import java.util.*;
final class Text{private static final List<String>VALUES=load();
private Text(){}static String text(int index){return VALUES.get(index);
}private static List<String>load(){List<String>values=new ArrayList<>();
for(String[]row:ResourceTable.decodedRows("slayer-text.tsv",2)){int index=Integer.parseInt(row[0]);
if(index!=values.size()){throw new IllegalStateException("Invalid Slayer text index "+index);
}values.add(row[1]);
}return Collections.unmodifiableList(values);
}}
