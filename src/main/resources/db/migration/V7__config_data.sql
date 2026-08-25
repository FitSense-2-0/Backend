-- =====================================================================
-- FitSense MVP 1.0 - V7: Configuracion de calculo MVP-1.0
-- Diseno: seccion 16
-- =====================================================================

INSERT INTO calculation_configs (version, description, is_active, params) VALUES (
    'MVP-1.0',
    'Configuracion inicial del MVP. Umbrales sin calibrar: se ajustan tras el piloto (Etapa 4).',
    TRUE,
    $json${
      "adherence": {
        "session_completed_threshold_pct": 80.0,
        "session_valid_threshold_pct": 30.0,
        "exercise_completed_threshold_pct": 80.0,
        "completion_cap_pct": 100.0,
        "primary_metric": "weighted_adherence_pct",
        "valid_attempt_rule": "LAST_FINISHED"
      },
      "adjustment": {
        "good_threshold_pct": 80.0,
        "moderate_threshold_pct": 50.0,
        "moderate_volume_reduction_pct": 20.0,
        "low_volume_reduction_pct": 30.0,
        "fatigue_extra_reduction_pct": 10.0,
        "progression_increase_pct": 5.0,
        "volume_tolerance_pct": 5.0,
        "load_reduction_pct": 15.0,
        "max_load_reduction_pct": 20.0,
        "max_cumulative_volume_reduction_pct": 40.0,
        "recovery_step_pct": 10.0,
        "low_days_reduction": 1,
        "min_days_per_week": 2,
        "min_session_minutes": 20,
        "min_exercises_per_session": 2,
        "min_sets_per_exercise": 2,
        "min_reps_per_set": 6,
        "min_duration_seconds": 20,
        "duration_to_reps_divisor": 30
      },
      "risk": {
        "adherence_points": [
          {"min_pct": 80.0, "points": 0},
          {"min_pct": 60.0, "points": 10},
          {"min_pct": 40.0, "points": 20},
          {"min_pct": 0.0,  "points": 30}
        ],
        "consecutive_skips_points": [
          {"min_count": 3, "points": 25},
          {"min_count": 2, "points": 15},
          {"min_count": 1, "points": 5},
          {"min_count": 0, "points": 0}
        ],
        "drop_points": [
          {"min_drop_pp": 25.0, "points": 15},
          {"min_drop_pp": 15.0, "points": 5},
          {"min_drop_pp": 0.0,  "points": 0}
        ],
        "overexertion_points": 10,
        "rpe_threshold": 8.0,
        "levels": {"LOW": 0.0, "MODERATE": 25.0, "HIGH": 50.0, "CRITICAL": 70.0}
      },
      "dropout": {
        "days_without_workout": 14
      }
    }$json$::JSONB
);
