package com.dimmil.bugtracker.services;

import com.dimmil.bugtracker.entities.Project;
import com.dimmil.bugtracker.entities.User;
import com.dimmil.bugtracker.entities.enums.ProjectPriority;
import com.dimmil.bugtracker.entities.enums.RoleEnum;
import com.dimmil.bugtracker.entities.requests.project.CreateProjectRequest;
import com.dimmil.bugtracker.entities.requests.project.EditProjectRequest;
import com.dimmil.bugtracker.entities.responses.project.ProjectNameResponse;
import com.dimmil.bugtracker.entities.responses.project.ProjectPreviewResponse;
import com.dimmil.bugtracker.entities.responses.project.ProjectResponse;
import com.dimmil.bugtracker.entities.responses.user.UserNameResponse;
import com.dimmil.bugtracker.entities.responses.user.UserResponse;
import com.dimmil.bugtracker.exceptions.project.ProjectNotFoundException;
import com.dimmil.bugtracker.exceptions.user.UserActionForbiddenException;
import com.dimmil.bugtracker.projections.dashboard.projectCountByPriority;
import com.dimmil.bugtracker.repositories.ProjectRepository;
import com.dimmil.bugtracker.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TicketService ticketService;


    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public void createProject(CreateProjectRequest createProjectRequest, Long owner_id) {
        User owner = userService.findById(owner_id);

        var users = userRepository.findAllById(createProjectRequest.getUsers());
        var userSet = new HashSet<>(users);
        userSet.add(owner);

        Project project = new Project(null, createProjectRequest.getTitle(),
                createProjectRequest.getDescription(),
                ProjectPriority.getPriority(createProjectRequest.getPriority()),
                LocalDate.now(),
                createProjectRequest.getDeadline(),
                owner,
                userSet
                );

        projectRepository.save(project);

    }

    public List<ProjectNameResponse> getProjectsThatUserIsPartOf(User user) {
        List<Project> queryData;
        if (user.getRole() == RoleEnum.ROLE_ADMIN) {
            queryData = projectRepository.getProjectsAdmin();
        } else {
            queryData = projectRepository.getProjectsThatUserIsPartOf(user.getId());
        }
        var response = new ArrayList<ProjectNameResponse>();

        for (var project : queryData) {
            response.add(
                    ProjectNameResponse.builder()
                            .projectId(project.getId())
                            .fullName(project.getTitle())
                            .build()
            );
        }
        return response;
    }

    public List<ProjectPreviewResponse> getAllProjectsWithUserNameOnly(User user) {
        List<Project> projects;
        if (user.getRole() == RoleEnum.ROLE_ADMIN) {
            projects = projectRepository.getProjectsAdmin();
        } else {
            projects = projectRepository.getProjectsThatUserIsPartOf(user.getId());
        }
        List<ProjectPreviewResponse> response = new ArrayList<>();
        for (Project project : projects) {
            var users = userService.getTop3UsersofProject(project.getId());
            List<UserNameResponse> userResponse = new ArrayList<>();
            for (User u : users) {
                userResponse.add(
                        UserNameResponse.builder()
                                .id(u.getId())
                                .fullName(u.getFullName())
                                .build()
                );
            }

            var owner = UserResponse.builder()
                    .id(project.getOwner().getId())
                    .fullName(project.getOwner().getFullName())
                    .role(project.getOwner().getRole().name())
                    .build();

            response.add(
                    ProjectPreviewResponse.builder().id(project.getId())
                            .projectName(project.getTitle())
                            .priority(project.getPriority().getLabel())
                            .startDate(project.getStartDate())
                            .deadline(project.getDeadline())
                            .users(userResponse)
                            .ownerUser(owner)
                            .build()
            );
        }
        return response;
    }

    public ProjectResponse getProjectById(User user,UUID projectId) {

        if (!userService.isUserInProject(projectId, user.getId()) && user.getRole() != RoleEnum.ROLE_ADMIN) {
            throw new UserActionForbiddenException();
        }

        Project project = projectRepository.findById(projectId).orElseThrow(
                () -> new ProjectNotFoundException(projectId));

        var owner = UserResponse.builder()
                .id(project.getOwner().getId())
                .fullName(project.getOwner().getFullName())
                .role(project.getOwner().getRole().name())
                .build();

        List<UserResponse> userResponse = new ArrayList<>();
        for (User u : project.getUsers()) {
            if (u.getId().equals(owner.getId())) continue;
            userResponse.add(
                    UserResponse.builder()
                            .id(u.getId())
                            .fullName(u.getFullName())
                            .role(u.getRole().name())
                            .build()
            );
        }

        var projectTickets = ticketService.getAllProjectTicketsByStatus(projectId);

        return ProjectResponse.builder()
                .id(project.getId())
                .projectName(project.getTitle())
                .description(project.getDescription())
                .priority(project.getPriority().getLabel())
                .startDate(project.getStartDate())
                .deadline(project.getDeadline())
                .ownerUser(owner)
                .users(userResponse)
                .tickets(projectTickets)
                .build();
    }

    public Long getNumberOfProjects(User user) {
        if (user.getRole() == RoleEnum.ROLE_ADMIN) {
            return projectRepository.countProjectsAdmin();
        } else {
            return projectRepository.countProjects(user.getId());
        }
    }

    public List<projectCountByPriority> getProjectsCountByPriority(User user) {
        if (user.getRole() == RoleEnum.ROLE_ADMIN) {
            return projectRepository.countProjectsByPriorityAdmin();
        } else {
            return projectRepository.countProjectsByPriority(user.getId());
        }
    }

    public List<Project> get5ProjectsWithDeadlineClose(User user) {
        LocalDate today = LocalDate.now();
        LocalDate nextMonth = today.plusMonths(1);
        if (user.getRole() == RoleEnum.ROLE_ADMIN) {
            return projectRepository.getProjectsWithDeadlineCloseAdmin(today,nextMonth, Limit.of(5));
        } else {
            return projectRepository.getProjectsWithDeadlineClose(user.getId(),today,nextMonth, Limit.of(5));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public void updateProject(EditProjectRequest editProjectRequest, User user, UUID projectId) {

        if (!userService.isUserInProject(projectId, user.getId()) && user.getRole() != RoleEnum.ROLE_ADMIN) {
            throw new UserActionForbiddenException();
        }

        Project project = projectRepository.findById(projectId).orElseThrow(
                () -> new ProjectNotFoundException(projectId));

        User owner = userService.findById(editProjectRequest.getOwnerId());

        //TODO: Check if removing developer with assigned tickets.
        var users = userRepository.findAllById(editProjectRequest.getUsers());
        var userSet = new HashSet<>(users);
        userSet.add(owner);

        project.setTitle(editProjectRequest.getTitle());
        project.setDescription(editProjectRequest.getDescription());
        project.setPriority(ProjectPriority.getPriority(editProjectRequest.getPriority()));
        project.setDeadline(editProjectRequest.getDeadline());
        project.setUsers(userSet);

        projectRepository.save(project);
    }
}
