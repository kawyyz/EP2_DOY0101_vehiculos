package cl.matiivilla.vehiculos.controller;

import cl.matiivilla.vehiculos.dto.VehiculoResponse;
import cl.matiivilla.vehiculos.service.VehiculoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VehiculoController.class)
class VehiculoControllerTest {

    private static final String BASE = "/api/v1/vehiculos";

    private static final String JSON_VALIDO = """
            {"patente":"AAAA11","marca":"Toyota","modelo":"Yaris","anio":2020,
             "color":"Rojo","tipo":"Sedan","disponible":true}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehiculoService vehiculoService;

    private VehiculoResponse respuesta() {
        return VehiculoResponse.builder()
                .id(1L).patente("AAAA11").marca("Toyota").modelo("Yaris")
                .anio(2020).color("Rojo").tipo("Sedan").disponible(true)
                .build();
    }

    @Test
    void get_lista_devuelve200() throws Exception {
        when(vehiculoService.findAll()).thenReturn(List.of(respuesta()));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patente").value("AAAA11"));
    }

    @Test
    void get_porId_devuelve200() throws Exception {
        when(vehiculoService.findById(1L)).thenReturn(respuesta());

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marca").value("Toyota"));
    }

    @Test
    void get_porIdInexistente_devuelve404() throws Exception {
        when(vehiculoService.findById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "no existe"));

        mockMvc.perform(get(BASE + "/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_valido_devuelve201() throws Exception {
        when(vehiculoService.create(any())).thenReturn(respuesta());

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void post_sinPatente_devuelve400() throws Exception {
        String invalido = """
                {"patente":"","marca":"Toyota","modelo":"Yaris","anio":2020,"tipo":"Sedan","disponible":true}""";

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_anioMenorA1950_devuelve400() throws Exception {
        String invalido = """
                {"patente":"AAAA11","marca":"Toyota","modelo":"Yaris","anio":1900,"tipo":"Sedan","disponible":true}""";

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_valido_devuelve200() throws Exception {
        when(vehiculoService.update(eq(1L), any())).thenReturn(respuesta());

        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patente").value("AAAA11"));
    }

    @Test
    void delete_existente_devuelve204() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_inexistente_devuelve404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "no existe"))
                .when(vehiculoService).delete(99L);

        mockMvc.perform(delete(BASE + "/99"))
                .andExpect(status().isNotFound());
    }
}
