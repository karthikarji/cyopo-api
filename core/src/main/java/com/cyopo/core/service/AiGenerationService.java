package com.cyopo.core.service;

import com.cyopo.core.dto.request.AiExperienceRequest;
import com.cyopo.core.dto.request.AiProfileRequest;
import com.cyopo.core.dto.request.AiProjectsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationService {

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public Object generateProfile(AiProfileRequest request) {
        String prompt = buildProfilePrompt(
                request.getIndustry(), request.getRole());
        return callOpenAi(prompt, Object.class);
    }

    public Object generateExperience(AiExperienceRequest request) {
        String prompt = buildExperiencePrompt(
                request.getName(),
                request.getTitle(),
                request.getBio());
        return callOpenAi(prompt, Object.class);
    }

    public Object generateProjects(AiProjectsRequest request) {
        String prompt = buildProjectsPrompt(
                request.getName(),
                request.getTitle(),
                request.getBio(),
                request.getTechnologies());
        return callOpenAi(prompt, Object.class);
    }

    private <T> T callOpenAi(String prompt, Class<T> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.7
            );

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, headers);

            Map<?, ?> response = restTemplate.postForObject(
                    "https://api.openai.com/v1/chat/completions",
                    entity,
                    Map.class
            );

            if (response == null) return null;

            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0))
                    .get("message");
            String content = (String) message.get("content");

            // Parse JSON response from OpenAI
            return objectMapper.readValue(content, responseType);

        } catch (Exception ex) {
            log.error("OpenAI API call failed: {}", ex.getMessage());
            return null;
        }
    }

    private String buildProfilePrompt(String industry, String role) {
        return String.format("""
            Generate realistic professional profile data for a portfolio.
            %s
            %s
            
            Return ONLY valid JSON with this exact structure:
            {
              "name": "Full Name",
              "title": "Professional Title",
              "bio": "Professional bio 2-3 sentences",
              "email": "professional@example.com",
              "phone": "+1 555-XXX-XXXX",
              "location": "City, State",
              "website": "https://personalwebsite.com",
              "profilePhoto": "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400",
              "socialMedia": [
                {"platform": "LinkedIn", "url": "https://linkedin.com/in/username"},
                {"platform": "GitHub", "url": "https://github.com/username"}
              ]
            }
            """,
                industry != null ? "Industry: " + industry : "",
                role != null ? "Role: " + role : ""
        );
    }

    private String buildExperiencePrompt(
            String name, String title, String bio) {
        return String.format("""
            Generate realistic work experience data for a portfolio.
            %s
            
            Return ONLY a valid JSON array with 2-3 experiences:
            [
              {
                "title": "Job Title",
                "company": "Company Name",
                "location": "City, Country",
                "startDate": "2021-05-01",
                "endDate": "2023-06-01",
                "isCurrent": false,
                "description": "Job description 2-3 sentences",
                "achievements": ["Achievement 1", "Achievement 2"],
                "technologies": ["React", "TypeScript", "Node.js"]
              }
            ]
            """,
                name != null
                        ? String.format(
                        "Context: Name: %s, Title: %s, Bio: %s",
                        name, title, bio)
                        : ""
        );
    }

    private String buildProjectsPrompt(
            String name, String title, String bio,
            List<String> technologies) {
        return String.format("""
            Generate realistic project data for a portfolio.
            %s
            %s
            
            Return ONLY a valid JSON array with 3-4 projects:
            [
              {
                "title": "Project Name",
                "description": "Project description 2-3 sentences",
                "thumbnail": "https://images.unsplash.com/photo-1559311648-d46f5d8593d6?w=400",
                "demoUrl": "https://project.com",
                "githubUrl": "https://github.com/username/project",
                "technologies": ["React", "TypeScript"],
                "isFeatured": true
              }
            ]
            """,
                name != null
                        ? String.format(
                        "Context: Name: %s, Title: %s, Bio: %s",
                        name, title, bio)
                        : "",
                technologies != null && !technologies.isEmpty()
                        ? "Preferred technologies: " + technologies
                        : ""
        );
    }
}