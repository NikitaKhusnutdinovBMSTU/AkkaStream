package lab5.bmstu;

import javafx.util.Pair;

public class GetMSG {
    Pair<String, Integer> msgPair;

    public GetMSG(Pair<String, Integer> pair){
        this.msgPair = pair;
    }

    public String getURL(){
        return msgPair.getKey();
    }

    public int getCount(){
        return msgPair.getValue();
    }
}
