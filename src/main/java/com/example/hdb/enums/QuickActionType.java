package com.example.hdb.enums;

public enum QuickActionType {
    STRONGER_ACTION("더 강한 액션"),
    DARKER_MOOD("더 어두운 분위기"),
    DRAMATIC_LIGHTING("더 극적인 조명"),
    CAMERA_FAR("카메라 더 멀리"),
    CAMERA_CLOSE("카메라 더 가깝게"),
    ADD_BACKGROUND_CHARACTERS("배경 인물 추가"),
    ENHANCE_EFFECTS("효과 강화");
    
    private final String label;
    
    QuickActionType(String label) {
        this.label = label;
    }
    
    public String getLabel() {
        return label;
    }
}
