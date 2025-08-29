package com.example.smartspring.web;


import com.example.smartspring.service.AssistantService;
import com.example.smartspring.service.AssistantService.PatientSummary;
import com.example.smartspring.service.AssistantService.ReportWithObservations;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/assistant")
public class AssistantController {

    private final AssistantService svc;

    public AssistantController(AssistantService svc) {
        this.svc = svc;
    }

    // Home: lista de pacientes + acciones
    @GetMapping
    public String index(@RequestParam(defaultValue = "10") int maxPatients,
                        Model model, HttpSession session) {
        var patients = svc.listPatients(session, Math.max(1, Math.min(maxPatients, 50)));
        model.addAttribute("patients", patients);
        model.addAttribute("maxPatients", maxPatients);
        return "assistant/index";
    }

    // Detalle por paciente
    @GetMapping("/patient/{patientId}")
    public String byPatient(@PathVariable String patientId,
                            @RequestParam(required = false) String category, // laboratory|vital-signs...
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue = "25") int maxReports,
                            Model model, HttpSession session) {
        var patient = svc.getPatientSummary(session, patientId);
        var reports = svc.getReportsForPatient(session, patientId, category, from, to, Math.max(1, Math.min(maxReports, 100)));
        model.addAttribute("patient", patient);
        model.addAttribute("reports", reports);
        var filters = new LinkedHashMap<String, Object>();
        filters.put("category", category == null ? "" : category);
        filters.put("from", from == null ? "" : from.toString());
        filters.put("to", to == null ? "" : to.toString());
        filters.put("maxReports", maxReports);
        model.addAttribute("filters", filters);
        return "assistant/patient";
    }

    // Todos los pacientes (ligero: N pacientes)
    @GetMapping("/all")
    public String all(@RequestParam(defaultValue = "5") int maxPatients,
                      @RequestParam(required = false) String category,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(defaultValue = "10") int maxReports,
                      Model model, HttpSession session) {
        var patients = svc.listPatients(session, Math.max(1, Math.min(maxPatients, 20)));
        var sections = new LinkedHashMap<PatientSummary, List<ReportWithObservations>>();
        for (var p : patients) {
            sections.put(p, svc.getReportsForPatient(session, p.id(), category, from, to, Math.max(1, Math.min(maxReports, 50))));
        }
        model.addAttribute("sections", sections);
        model.addAttribute("maxPatients", maxPatients);
        var filters = new LinkedHashMap<String, Object>();
        filters.put("category", category == null ? "" : category);
        filters.put("from", from == null ? "" : from.toString());
        filters.put("to", to == null ? "" : to.toString());
        filters.put("maxReports", maxReports);
        model.addAttribute("filters", filters);
        return "assistant/all";
    }
}