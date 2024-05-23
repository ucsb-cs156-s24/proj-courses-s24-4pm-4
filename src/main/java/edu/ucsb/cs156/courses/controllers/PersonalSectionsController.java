package edu.ucsb.cs156.courses.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.courses.documents.Course;
import edu.ucsb.cs156.courses.entities.PSCourse;
import edu.ucsb.cs156.courses.entities.PersonalSchedule;
import edu.ucsb.cs156.courses.entities.User;
import edu.ucsb.cs156.courses.errors.EntityNotFoundException;
import edu.ucsb.cs156.courses.repositories.PSCourseRepository;
import edu.ucsb.cs156.courses.repositories.PersonalScheduleRepository;
import edu.ucsb.cs156.courses.services.UCSBCurriculumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Personal Sections")
@RequestMapping("/api/personalSections")
@RestController
@Slf4j
public class PersonalSectionsController extends ApiController {
  @Autowired PersonalScheduleRepository personalScheduleRepository;

  @Autowired PSCourseRepository coursesRepository;

  @Autowired private ObjectMapper objectMapper;

  @Autowired UCSBCurriculumService ucsbCurriculumService;

  @Operation(summary = "List all sections given a psId")
  @PreAuthorize("hasRole('ROLE_USER')")
  @GetMapping(value = "/all", produces = "application/json")
  public ArrayList<Course> getSectionsByPsId(@Parameter(name = "psId") @RequestParam Long psId)
      throws JsonProcessingException {
    User us = getCurrentUser().getUser();
    PersonalSchedule ps =
        personalScheduleRepository
            .findByIdAndUser(psId, us)
            .orElseThrow(() -> new EntityNotFoundException(PersonalSchedule.class, psId));
    ArrayList<Course> sections = new ArrayList<Course>();
    ArrayList<String> jsons = new ArrayList<String>();
    Iterable<PSCourse> courses = coursesRepository.findAllByPsId(psId);
    for (PSCourse crs : courses) {

      User u = crs.getUser();
      String qtr = ps.getQuarter();
      String responseBody = ucsbCurriculumService.getJSONbyQtrEnrollCd(qtr, crs.getEnrollCd());
      jsons.add(responseBody);
      Course course = objectMapper.readValue(responseBody, Course.class);
      sections.add(course);
    }
    return sections;
  }

  @Operation(summary = "Delete a schedule and associated lectures by enroll code and psId")
    @PreAuthorize("hasRole('ROLE_USER')")
    @DeleteMapping(value = "/delete")
    public Object deleteScheduleAndLectures(@Parameter(name = "psId") @RequestParam Long psId,
                                            @Parameter(name = "enrollCd") @RequestParam String enrollCd)
            throws JsonProcessingException {
        User currentUser = getCurrentUser().getUser();

        PersonalSchedule ps = personalScheduleRepository.findByIdAndUser(psId, currentUser)
                .orElseThrow(() -> new EntityNotFoundException(PersonalSchedule.class, psId));

        Iterable<PSCourse> courses = coursesRepository.findAllByPsId(psId);

        boolean courseFound = false;

        ArrayList<String> relatedEnrollCodes = new ArrayList<>();
        String body = ucsbCurriculumService.getAllSections(enrollCd, ps.getQuarter());
        if (!body.equals("{\"error\": \"401: Unauthorized\"}") && !body.equals("{\"error\": \"Enroll code doesn't exist in that quarter.\"}")) {
            Iterator<JsonNode> it = objectMapper.readTree(body).path("classSections").elements();
            while (it.hasNext()) {
                JsonNode classSection = it.next();
                String sectionEnrollCd = classSection.path("enrollCode").asText();
                relatedEnrollCodes.add(sectionEnrollCd);
            }
        }

        for (PSCourse crs : courses) {
            if (relatedEnrollCodes.contains(crs.getEnrollCd())) {
                courseFound = true;
                coursesRepository.delete(crs);
            }
        }

        if (!courseFound) {
            throw new EntityNotFoundException(PSCourse.class, "enrollCd: " + enrollCd + " and psId: " + psId);
        }

        return genericMessage("Schedule with psId %s and associated lectures with enrollCd %s deleted".formatted(psId, enrollCd));
    }
}
