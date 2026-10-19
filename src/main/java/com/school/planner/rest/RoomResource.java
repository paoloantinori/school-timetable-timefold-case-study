package com.school.planner.rest;

import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.Room;

@Path("/api/rooms")
@Produces(MediaType.APPLICATION_JSON)
public class RoomResource {

    @GET
    public List<Room> list() {
        return Room.listAll();
    }

    @POST
    @Transactional
    public Room create(Room room) {
        room.persist();
        return room;
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Room update(@PathParam("id") Long id, Room updated) {
        Room room = Room.findById(id);
        if (room == null) {
            throw new jakarta.ws.rs.NotFoundException("Room not found: " + id);
        }
        room.name = updated.name;
        room.capacity = updated.capacity;
        room.tags = updated.tags;
        return room;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        Room.deleteById(id);
    }
}
