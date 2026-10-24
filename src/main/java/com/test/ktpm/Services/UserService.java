package com.test.ktpm.Services;

import com.test.ktpm.Mapper.UserMapper;
import com.test.ktpm.Model.Response.UserResponse;
import com.test.ktpm.Repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userMapper.toResponsePage(userRepository.findAll(pageable));
    }
}
