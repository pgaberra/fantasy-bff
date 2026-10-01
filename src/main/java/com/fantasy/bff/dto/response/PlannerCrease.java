package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One club's goalies over the stretch, night by night. A free agent's starts on the nights a
 * client counts depend on who else is in his crease, the goalies the league has rostered as well
 * as the ones it has available, so the crease goes out whole rather than as his line alone.
 */
@Schema(description = "One club's goalies and each one's chance of starting each of its games in the stretch")
public record PlannerCrease(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The club, in the model's spelling")
                String team,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every goalie the model projects for the club, the most starts over the "
                        + "stretch first, which is the order a tie goes by")
                List<CreaseGoalie> goalies) {}
