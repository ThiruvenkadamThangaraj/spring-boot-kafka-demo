package com.example.common.util;

import com.example.common.dto.UserCreateRequest;
import com.example.common.dto.UserDTO;
import com.example.common.entity.User;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class EntityMapper {

    private static final ModelMapper modelMapper = new ModelMapper();

    /**
     * Automatically maps all fields from UserCreateRequest to User entity.
     * When you add new fields to common module, they will automatically be mapped.
     */
    public static User toEntity(UserCreateRequest request) {
        return modelMapper.map(request, User.class);
    }

    /**
     * Automatically maps all fields from User entity to UserDTO.
     * When you add new fields to common module, they will automatically be mapped.
     */
    public static UserDTO toDTO(User user) {
        return modelMapper.map(user, UserDTO.class);
    }

    /**
     * Updates existing User entity from UserCreateRequest.
     * Only updates fields that exist in the request, preserves others.
     */
    public static void updateEntityFromRequest(User user, UserCreateRequest request) {
        modelMapper.map(request, user);
    }
}
