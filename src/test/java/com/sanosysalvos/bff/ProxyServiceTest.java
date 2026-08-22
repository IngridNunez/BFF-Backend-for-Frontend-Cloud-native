package com.sanosysalvos.bff;

import com.sanosysalvos.bff.config.ServiciosProperties;
import com.sanosysalvos.bff.service.ProxyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
public class ProxyServiceTest {

    @Mock
    private ServiciosProperties serviciosProperties;

    @Mock
    private RestClient restClient;

    @InjectMocks
    private ProxyService proxyService;

}

