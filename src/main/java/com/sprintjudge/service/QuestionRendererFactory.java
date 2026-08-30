package com.sprintjudge.service;

/**
 * Backend marker for the Question Renderer strategy. The actual rendering
 * happens on the frontend; this exists so the type registry is mirrored
 * server-side and to make future server-side rendering trivially expandable.
 */
public final class QuestionRendererFactory {

    private QuestionRendererFactory() {}

    public static boolean isValidType(String type) {
        if (type == null) return false;
        try {
            com.sprintjudge.domain.enums.QuestionType.from(type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
