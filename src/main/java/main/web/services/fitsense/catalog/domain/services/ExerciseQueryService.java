package main.web.services.fitsense.catalog.domain.services;

import main.web.services.fitsense.catalog.domain.model.aggregates.Exercise;
import main.web.services.fitsense.catalog.domain.model.queries.GetActiveExercisesQuery;
import main.web.services.fitsense.catalog.domain.model.queries.GetEligibleExercisesQuery;
import main.web.services.fitsense.catalog.domain.model.queries.GetExerciseByIdQuery;

import java.util.List;
import java.util.Optional;

public interface ExerciseQueryService {
    List<Exercise> handle(GetActiveExercisesQuery query);
    List<Exercise> handle(GetEligibleExercisesQuery query);
    Optional<Exercise> handle(GetExerciseByIdQuery query);
}
