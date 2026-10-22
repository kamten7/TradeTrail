package com.tradetrail.simulator;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DeliveryController {
    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("events", service.list());
        return "index";
    }

    @PostMapping("/events")
    public String create(@RequestParam String trackingNo, @RequestParam String status,
                         @RequestParam String location) {
        TrackingRow event = service.create(trackingNo, status, location);
        service.deliver(event.eventId);
        return "redirect:/";
    }

    @PostMapping("/events/{eventId}/retry")
    public String retry(@PathVariable String eventId) {
        service.deliver(eventId);
        return "redirect:/";
    }
}
