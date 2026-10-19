package com.school.planner.rest;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.school.planner.service.PrintViewService;

/**
 * TASK-659 (b)+(c): the printable timetable sheet. ONE server-rendered document,
 * two representations: {@code /print/{kind}/{id}} is the dedicated view (a clean
 * page with no app chrome, directly printable from the browser), and
 * {@code /print/{kind}/{id}.pdf} is the same document rendered server-side to a
 * PDF. The UI links here from the timeline header; the pages are also shareable
 * standalone (they carry their own school-year header and subject).
 *
 * <p>Kind is Italian on the wire ({@code docente|classe|aula}): these URLs are
 * read by school staff, not by scripts.</p>
 */
@Path("/print")
public class PrintResource {

    @Inject
    PrintViewService printViewService;

    /**
     * The (b) dedicated view. {@code week} is any date inside the wanted week
     * (defaults to today); the sheet is Monday-Friday around it.
     */
    @GET
    @Path("/{kind}/{id}")
    @Produces(MediaType.TEXT_HTML + ";charset=utf-8")
    public Response html(@PathParam("kind") String kind, @PathParam("id") long id,
            @QueryParam("week") String week) {
        PrintViewService.Kind k = PrintViewService.Kind.fromWire(kind);
        if (k == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("tipo sconosciuto: docente, classe o aula").build();
        }
        String html = printViewService.renderWeekSheet(k, id, weekAnchor(week), true);
        if (html == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("elemento inesistente").build();
        }
        return Response.ok(html).build();
    }

    /** The (c) server-side PDF of the very same sheet (toolbar omitted). */
    @GET
    @Path("/{kind}/{id}.pdf")
    @Produces("application/pdf")
    public Response pdf(@PathParam("kind") String kind, @PathParam("id") long id,
            @QueryParam("week") String week) throws Exception {
        PrintViewService.Kind k = PrintViewService.Kind.fromWire(kind);
        if (k == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String html = printViewService.renderWeekSheet(k, id, weekAnchor(week), false);
        if (html == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        builder.run();
        return Response.ok(out.toByteArray())
                .header("Content-Disposition", "inline; filename=\"orario-" + kind + "-" + id + ".pdf\"")
                .build();
    }

    private LocalDate weekAnchor(String week) {
        if (week != null && !week.isBlank()) {
            try {
                return LocalDate.parse(week);
            } catch (DateTimeParseException e) {
                // fall through to today: a malformed week param must not 500 a print link
            }
        }
        return LocalDate.now();
    }
}
