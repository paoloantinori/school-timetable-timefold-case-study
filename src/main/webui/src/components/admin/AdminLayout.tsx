import { Routes, Route, NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import TeachersAdmin from './TeachersAdmin';
import StudentGroupsAdmin from './StudentGroupsAdmin';
import RoomsAdmin from './RoomsAdmin';
import CoursesAdmin from './CoursesAdmin';
import HolidaysAdmin from './HolidaysAdmin';
import StagePatternsAdmin from './StagePatternsAdmin';
import GroupCalendarAdmin from './GroupCalendarAdmin';
import ProfessionalizzantiDaysAdmin from './ProfessionalizzantiDaysAdmin';
import TeacherAvailAdmin from './TeacherAvailAdmin';
import TeacherWeeklyGridAdmin from './TeacherWeeklyGridAdmin';
import CsvImportWizard from './CsvImportWizard';
import LabAssistantsAdmin from './LabAssistantsAdmin';
import SchoolSettingsAdmin from './SchoolSettingsAdmin';

export default function AdminLayout() {
  const { t } = useTranslation('admin');

  return (
    <div className="flex gap-4">
      <nav className="w-[200px] bg-surface rounded-lg p-3 shadow-soft">
        <NavLink
          to="/admin/teachers"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('teachers')}
        </NavLink>
        <NavLink
          to="/admin/student-groups"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('studentGroups')}
        </NavLink>
        <NavLink
          to="/admin/rooms"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('rooms')}
        </NavLink>
        <NavLink
          to="/admin/courses"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('courses')}
        </NavLink>
        <NavLink
          to="/admin/holidays"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('holidays')}
        </NavLink>
        <NavLink
          to="/admin/stage-patterns"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('stagePatterns')}
        </NavLink>
        <NavLink
          to="/admin/group-calendar"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('groupCalendar')}
        </NavLink>
        <NavLink
          to="/admin/professionalizzanti-days"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('professionalizzantiDays')}
        </NavLink>
        <NavLink
          to="/admin/teacher-availability"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('teacherAvailability')}
        </NavLink>
        <NavLink
          to="/admin/teacher-weekly-grid"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('teacherWeeklyGrid')}
        </NavLink>
        <NavLink
          to="/admin/lab-assistants"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('labAssistants')}
        </NavLink>
        <NavLink
          to="/admin/import"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('csvImport')}
        </NavLink>
        <NavLink
          to="/admin/school-settings"
          className={({ isActive }) =>
            `block px-3 py-2 text-muted no-underline rounded text-sm mb-1
             ${isActive ? 'bg-accent text-accent-text font-semibold' : 'hover:bg-surface-hover'}`
          }
        >
          {t('schoolSettings')}
        </NavLink>
      </nav>
      <div className="flex-1">
        <Routes>
          <Route path="teachers" element={<TeachersAdmin />} />
          <Route path="student-groups" element={<StudentGroupsAdmin />} />
          <Route path="rooms" element={<RoomsAdmin />} />
          <Route path="courses" element={<CoursesAdmin />} />
          <Route path="holidays" element={<HolidaysAdmin />} />
          <Route path="stage-patterns" element={<StagePatternsAdmin />} />
          <Route path="group-calendar" element={<GroupCalendarAdmin />} />
          <Route path="professionalizzanti-days" element={<ProfessionalizzantiDaysAdmin />} />
          <Route path="teacher-availability" element={<TeacherAvailAdmin />} />
          <Route path="teacher-weekly-grid" element={<TeacherWeeklyGridAdmin />} />
          <Route path="lab-assistants" element={<LabAssistantsAdmin />} />
          <Route path="import" element={<CsvImportWizard />} />
          <Route path="school-settings" element={<SchoolSettingsAdmin />} />
          <Route path="*" element={<TeachersAdmin />} />
        </Routes>
      </div>
    </div>
  );
}
