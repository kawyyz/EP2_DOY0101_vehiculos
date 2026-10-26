package cl.matiivilla.vehiculos.service;

import cl.matiivilla.vehiculos.dto.VehiculoRequest;
import cl.matiivilla.vehiculos.dto.VehiculoResponse;
import cl.matiivilla.vehiculos.model.Vehiculo;
import cl.matiivilla.vehiculos.repository.VehiculoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehiculoServiceTest {

    @Mock
    private VehiculoRepository vehiculoRepository;

    @InjectMocks
    private VehiculoService vehiculoService;

    private Vehiculo vehiculo(Long id, String patente) {
        return Vehiculo.builder()
                .id(id).patente(patente).marca("Toyota").modelo("Yaris")
                .anio(2020).color("Rojo").tipo("Sedan").disponible(true)
                .build();
    }

    private VehiculoRequest request(String patente) {
        VehiculoRequest r = new VehiculoRequest();
        r.setPatente(patente);
        r.setMarca("Toyota");
        r.setModelo("Yaris");
        r.setAnio(2020);
        r.setColor("Rojo");
        r.setTipo("Sedan");
        r.setDisponible(true);
        return r;
    }

    private HttpStatus status(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    void findAll_devuelveTodosLosVehiculos() {
        when(vehiculoRepository.findAll()).thenReturn(List.of(vehiculo(1L, "AAAA11"), vehiculo(2L, "BBBB22")));

        List<VehiculoResponse> resultado = vehiculoService.findAll();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getPatente()).isEqualTo("AAAA11");
    }

    @Test
    void findById_existente_devuelveVehiculo() {
        when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo(1L, "AAAA11")));

        VehiculoResponse resultado = vehiculoService.findById(1L);

        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getMarca()).isEqualTo("Toyota");
    }

    @Test
    void findById_inexistente_lanza404() {
        when(vehiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehiculoService.findById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void create_patenteNueva_guardaYDevuelve() {
        when(vehiculoRepository.existsByPatente("AAAA11")).thenReturn(false);
        when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(inv -> {
            Vehiculo v = inv.getArgument(0);
            v.setId(10L);
            return v;
        });

        VehiculoResponse resultado = vehiculoService.create(request("AAAA11"));

        assertThat(resultado.getId()).isEqualTo(10L);
        assertThat(resultado.getPatente()).isEqualTo("AAAA11");
    }

    @Test
    void create_patenteDuplicada_lanza409YNoGuarda() {
        when(vehiculoRepository.existsByPatente("AAAA11")).thenReturn(true);

        assertThatThrownBy(() -> vehiculoService.create(request("AAAA11")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));

        verify(vehiculoRepository, never()).save(any());
    }

    @Test
    void update_existente_actualizaCampos() {
        when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo(1L, "AAAA11")));
        when(vehiculoRepository.findByPatente("CCCC33")).thenReturn(Optional.empty());
        when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        VehiculoResponse resultado = vehiculoService.update(1L, request("CCCC33"));

        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getPatente()).isEqualTo("CCCC33");
    }

    @Test
    void update_inexistente_lanza404() {
        when(vehiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehiculoService.update(99L, request("AAAA11")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void update_patenteDeOtroVehiculo_lanza409() {
        when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo(1L, "AAAA11")));
        when(vehiculoRepository.findByPatente("BBBB22")).thenReturn(Optional.of(vehiculo(2L, "BBBB22")));

        assertThatThrownBy(() -> vehiculoService.update(1L, request("BBBB22")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));

        verify(vehiculoRepository, never()).save(any());
    }

    @Test
    void delete_existente_elimina() {
        Vehiculo v = vehiculo(1L, "AAAA11");
        when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(v));

        vehiculoService.delete(1L);

        verify(vehiculoRepository).delete(v);
    }

    @Test
    void delete_inexistente_lanza404() {
        when(vehiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehiculoService.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(vehiculoRepository, never()).delete(any());
    }
}
