package com.sturdywaffle.web;

import com.sturdywaffle.infrastructure.persistence.ActivityQuery;
import com.sturdywaffle.web.dto.ActivityResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/activity")
@Profile("!eval")
public class ActivityController {

    private final ActivityQuery activityQuery;

    public ActivityController(ActivityQuery activityQuery) {
        this.activityQuery = activityQuery;
    }

    @GetMapping
    public List<ActivityResponse> list(@RequestParam(defaultValue = "100") int limit) {
        return activityQuery.recent(limit);
    }
}
